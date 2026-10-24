package com.example.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.database.DownloadTaskEntity
import com.example.database.NovaDatabase
import com.example.util.FormatUtils
import com.example.util.NetworkUtils
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class NovaDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private lateinit var notificationManager: NotificationManager
    private lateinit var database: NovaDatabase

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        database = NovaDatabase.getDatabase(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Media Download"
                val resolution = intent.getStringExtra(EXTRA_RESOLUTION) ?: "Original"
                startDownloadTask(taskId, url, title, resolution)
            }
            ACTION_PAUSE -> pauseDownloadTask(taskId)
            ACTION_RESUME -> resumeDownloadTask(taskId)
            ACTION_CANCEL -> cancelDownloadTask(taskId)
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nova Player Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live download progress and speed"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startDownloadTask(taskId: String, url: String, title: String, resolution: String) {
        if (activeJobs.containsKey(taskId)) return

        val job = serviceScope.launch {
            val tempDir = File(cacheDir, "nova_downloads").apply { mkdirs() }
            val tempFile = File(tempDir, "$taskId.tmp")

            // Determine safe clean filename and file extension
            val isHls = url.contains(".m3u8", ignoreCase = true)
            val extension = when {
                isHls -> ".mp4"
                url.contains(".mp3", ignoreCase = true) -> ".mp3"
                url.contains(".m4a", ignoreCase = true) -> ".m4a"
                url.contains(".mkv", ignoreCase = true) -> ".mkv"
                url.contains(".webm", ignoreCase = true) -> ".webm"
                url.contains(".ts", ignoreCase = true) -> ".ts"
                else -> ".mp4"
            }

            var safeTitle = title.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            if (!safeTitle.endsWith(extension, ignoreCase = true)) {
                safeTitle = "$safeTitle$extension"
            }

            // Record initial state in DB
            val task = DownloadTaskEntity(
                id = taskId,
                url = url,
                title = safeTitle,
                resolution = resolution,
                mimeType = if (extension == ".mp3" || extension == ".m4a") "audio/mpeg" else "video/mp4",
                tempFilePath = tempFile.absolutePath,
                status = "DOWNLOADING"
            )
            database.downloadDao().insertOrUpdate(task)

            updateNotification(taskId, safeTitle, 0, 0, 0, 0)

            try {
                if (isHls) {
                    downloadHlsStream(taskId, url, safeTitle, resolution, tempFile)
                } else {
                    downloadDirectStream(taskId, url, safeTitle, tempFile)
                }

                if (!isActive) return@launch

                // Finalize file: move from temp to permanent storage
                val saveDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(filesDir, "downloads")
                saveDir.mkdirs()
                val finalFile = File(saveDir, safeTitle)
                if (finalFile.exists()) finalFile.delete()

                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()

                // Register with MediaStore if possible
                val mediaStoreUri = tryRegisterMediaStore(finalFile, safeTitle, task.mimeType)
                val finalPathOrUri = mediaStoreUri?.toString() ?: finalFile.absolutePath

                val totalDownloaded = finalFile.length()
                database.downloadDao().markCompleted(
                    id = taskId,
                    localUri = finalPathOrUri,
                    totalBytes = totalDownloaded
                )

                showCompletedNotification(taskId, safeTitle)

            } catch (e: CancellationException) {
                // Task was paused or cancelled
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Download failed: connection error"
                database.downloadDao().updateStatus(taskId, "FAILED", errorMsg)
                showFailedNotification(taskId, safeTitle, errorMsg)
            } finally {
                activeJobs.remove(taskId)
                if (activeJobs.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                }
            }
        }

        activeJobs[taskId] = job
    }

    /**
     * Downloads an HLS stream by parsing master/media playlists and concatenating all .ts/.m4s segments.
     */
    private suspend fun downloadHlsStream(
        taskId: String,
        url: String,
        title: String,
        resolution: String,
        tempFile: File
    ) {
        val browserUserAgent = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", browserUserAgent)
            .header("Accept", "*/*")
            .build()

        val playlistBody = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code} fetching playlist")
            response.body?.string() ?: throw Exception("Empty HLS playlist")
        }

        var mediaPlaylistUrl = url
        // Check if master playlist
        if (playlistBody.contains("#EXT-X-STREAM-INF")) {
            val reader = BufferedReader(StringReader(playlistBody))
            var line = reader.readLine()
            var selectedUri: String? = null

            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                    val nextLine = reader.readLine()?.trim() ?: break
                    if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                        selectedUri = nextLine
                        if (resolution.isNotEmpty() && trimmed.contains(resolution)) {
                            break // Found matching variant
                        }
                    }
                }
                line = reader.readLine()
            }

            if (selectedUri != null) {
                mediaPlaylistUrl = NetworkUtils.resolveUrl(url, selectedUri)
            }
        }

        // Fetch media playlist
        val mediaPlaylistReq = Request.Builder()
            .url(mediaPlaylistUrl)
            .header("User-Agent", browserUserAgent)
            .header("Accept", "*/*")
            .build()

        val mediaPlaylistBody = okHttpClient.newCall(mediaPlaylistReq).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code} fetching segments")
            resp.body?.string() ?: throw Exception("Empty media playlist")
        }

        // Extract all segment URLs
        val segmentUrls = mutableListOf<String>()
        val reader = BufferedReader(StringReader(mediaPlaylistBody))
        var line = reader.readLine()
        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                segmentUrls.add(NetworkUtils.resolveUrl(mediaPlaylistUrl, trimmed))
            }
            line = reader.readLine()
        }

        if (segmentUrls.isEmpty()) {
            throw Exception("No video segments found in HLS playlist")
        }

        val totalSegments = segmentUrls.size
        var downloadedBytes = 0L
        val outputStream = FileOutputStream(tempFile, false)

        try {
            var lastUpdateTime = System.currentTimeMillis()
            var bytesSinceUpdate = 0L

            for (index in segmentUrls.indices) {
                if (!coroutineContext.isActive) break

                val segUrl = segmentUrls[index]
                val segReq = Request.Builder()
                    .url(segUrl)
                    .header("User-Agent", browserUserAgent)
                    .header("Accept", "*/*")
                    .build()

                okHttpClient.newCall(segReq).execute().use { segResp ->
                    if (segResp.isSuccessful) {
                        segResp.body?.byteStream()?.use { input ->
                            val buf = ByteArray(32 * 1024)
                            var r: Int
                            while (input.read(buf).also { r = it } != -1) {
                                if (!coroutineContext.isActive) break
                                outputStream.write(buf, 0, r)
                                downloadedBytes += r
                                bytesSinceUpdate += r
                            }
                        }
                    }
                }

                val now = System.currentTimeMillis()
                val delta = now - lastUpdateTime
                if (delta >= 1000 || index == totalSegments - 1) {
                    val speed = if (delta > 0) (bytesSinceUpdate * 1000) / delta else 0L
                    val percent = (((index + 1) * 100) / totalSegments).coerceIn(0, 99)
                    val eta = if (index > 0 && speed > 0) {
                        val remainingSegments = totalSegments - (index + 1)
                        val avgBytesPerSeg = downloadedBytes / (index + 1)
                        (remainingSegments * avgBytesPerSeg) / speed
                    } else 0L

                    database.downloadDao().updateProgress(
                        id = taskId,
                        downloaded = downloadedBytes,
                        total = if (index > 0) (downloadedBytes * totalSegments) / (index + 1) else 0L,
                        speed = speed,
                        eta = eta,
                        status = "DOWNLOADING"
                    )

                    updateNotification(taskId, title, percent, downloadedBytes, 0L, speed)
                    lastUpdateTime = now
                    bytesSinceUpdate = 0L
                }
            }
        } finally {
            outputStream.flush()
            outputStream.close()
        }
    }

    /**
     * Downloads a progressive stream (direct MP4/MKV/WebM/MP3) with resume support.
     */
    private suspend fun downloadDirectStream(
        taskId: String,
        url: String,
        title: String,
        tempFile: File
    ) {
        val browserUserAgent = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        var existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", browserUserAgent)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Connection", "keep-alive")

        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        var response = okHttpClient.newCall(requestBuilder.build()).execute()

        // If server returns 416 Range Not Satisfiable, clear file and retry from byte 0
        if (response.code == 416) {
            response.close()
            tempFile.delete()
            existingBytes = 0L
            val freshRequest = Request.Builder()
                .url(url)
                .header("User-Agent", browserUserAgent)
                .header("Accept", "*/*")
                .build()
            response = okHttpClient.newCall(freshRequest).execute()
        }

        if (!response.isSuccessful && response.code != 206) {
            val code = response.code
            val msg = response.message
            response.close()
            throw Exception("HTTP $code: $msg")
        }

        val isPartial = response.code == 206
        if (!isPartial && existingBytes > 0) {
            // Server did not accept Range; restart from beginning
            tempFile.delete()
            existingBytes = 0L
        }

        val contentLength = response.body?.contentLength() ?: -1L
        val totalBytes = if (isPartial && contentLength > 0) {
            existingBytes + contentLength
        } else if (contentLength > 0) {
            contentLength
        } else 0L

        val inputStream: InputStream = response.body?.byteStream()
            ?: throw Exception("Empty response body from server")

        val randomAccessFile = RandomAccessFile(tempFile, "rw")
        if (existingBytes > 0) {
            randomAccessFile.seek(existingBytes)
        } else {
            randomAccessFile.setLength(0)
        }

        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        var downloadedBytes = existingBytes
        var lastUpdateTime = System.currentTimeMillis()
        var bytesSinceLastUpdate = 0L

        try {
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (!coroutineContext.isActive) {
                    return
                }

                randomAccessFile.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                bytesSinceLastUpdate += bytesRead

                val now = System.currentTimeMillis()
                val timeDelta = now - lastUpdateTime
                if (timeDelta >= 1000) {
                    val speedBps = (bytesSinceLastUpdate * 1000) / timeDelta
                    val remainingBytes = if (totalBytes > downloadedBytes) totalBytes - downloadedBytes else 0L
                    val etaSeconds = if (speedBps > 0) remainingBytes / speedBps else 0L

                    database.downloadDao().updateProgress(
                        id = taskId,
                        downloaded = downloadedBytes,
                        total = totalBytes,
                        speed = speedBps,
                        eta = etaSeconds,
                        status = "DOWNLOADING"
                    )

                    val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                    updateNotification(taskId, title, percent, downloadedBytes, totalBytes, speedBps)

                    lastUpdateTime = now
                    bytesSinceLastUpdate = 0L
                }
            }
        } finally {
            randomAccessFile.close()
            inputStream.close()
            response.close()
        }
    }

    private fun tryRegisterMediaStore(file: File, title: String, mimeType: String): Uri? {
        return try {
            val isAudio = mimeType.startsWith("audio/")
            val values = ContentValues().apply {
                if (isAudio) {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, title)
                    put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/NovaPlayer")
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                } else {
                    put(MediaStore.Video.Media.DISPLAY_NAME, title)
                    put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/NovaPlayer")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (isAudio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                if (isAudio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = contentResolver.insert(collection, values) ?: return null

            contentResolver.openOutputStream(itemUri).use { outStream ->
                file.inputStream().use { inStream ->
                    inStream.copyTo(outStream ?: return null)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                if (isAudio) {
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                } else {
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                contentResolver.update(itemUri, values, null, null)
            }

            itemUri
        } catch (_: Exception) {
            null
        }
    }

    private fun pauseDownloadTask(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        serviceScope.launch {
            database.downloadDao().updateStatus(taskId, "PAUSED")
        }
        notificationManager.cancel(taskId.hashCode())
    }

    private fun resumeDownloadTask(taskId: String) {
        serviceScope.launch {
            val task = database.downloadDao().getDownloadById(taskId).firstOrNull() ?: return@launch
            database.downloadDao().updateStatus(taskId, "QUEUED")
            startDownloadTask(task.id, task.url, task.title, task.resolution)
        }
    }

    private fun cancelDownloadTask(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        serviceScope.launch {
            database.downloadDao().updateStatus(taskId, "CANCELED")
            val tempFile = File(cacheDir, "nova_downloads/$taskId.tmp")
            if (tempFile.exists()) tempFile.delete()
        }
        notificationManager.cancel(taskId.hashCode())
    }

    private fun updateNotification(
        taskId: String,
        title: String,
        percent: Int,
        downloaded: Long,
        total: Long,
        speedBps: Long
    ) {
        val speedText = FormatUtils.formatSpeed(speedBps)
        val progressText = "${FormatUtils.formatBytes(downloaded)} / ${if (total > 0) FormatUtils.formatBytes(total) else "..."} • $speedText"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText(progressText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
    }

    private fun showCompletedNotification(taskId: String, title: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(taskId.hashCode(), notification)
    }

    private fun showFailedNotification(taskId: String, title: String, reason: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("$title: $reason")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(taskId.hashCode(), notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "nova_downloads_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.example.download.ACTION_START"
        const val ACTION_PAUSE = "com.example.download.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.download.ACTION_RESUME"
        const val ACTION_CANCEL = "com.example.download.ACTION_CANCEL"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_RESOLUTION = "extra_resolution"

        fun enqueueDownloadService(context: Context, taskId: String, url: String, title: String, resolution: String) {
            val intent = Intent(context, NovaDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_RESOLUTION, resolution)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseDownloadService(context: Context, taskId: String) {
            val intent = Intent(context, NovaDownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
        }

        fun resumeDownloadService(context: Context, taskId: String, url: String, title: String, resolution: String) {
            enqueueDownloadService(context, taskId, url, title, resolution)
        }

        fun cancelDownloadService(context: Context, taskId: String) {
            val intent = Intent(context, NovaDownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
        }
    }
}
