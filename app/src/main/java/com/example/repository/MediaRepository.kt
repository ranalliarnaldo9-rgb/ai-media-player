package com.example.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.database.MediaDao
import com.example.database.MediaItemEntity
import com.example.model.MediaItemModel
import com.example.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepository(private val mediaDao: MediaDao) {

    val allVideos: Flow<List<MediaItemModel>> = mediaDao.getMediaByType("VIDEO").map { list ->
        list.map { it.toModel() }
    }

    val allAudio: Flow<List<MediaItemModel>> = mediaDao.getMediaByType("AUDIO").map { list ->
        list.map { it.toModel() }
    }

    val continueWatching: Flow<List<MediaItemModel>> = mediaDao.getContinueWatching().map { list ->
        list.map { it.toModel() }
    }

    val favorites: Flow<List<MediaItemModel>> = mediaDao.getFavoriteMedia().map { list ->
        list.map { it.toModel() }
    }

    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean) {
        mediaDao.setFavorite(id, !isFavorite)
    }

    suspend fun savePlaybackPosition(id: String, positionMs: Long) {
        mediaDao.updatePlaybackPosition(id, positionMs, System.currentTimeMillis())
    }

    suspend fun deleteMedia(id: String) {
        mediaDao.deleteMediaById(id)
    }

    suspend fun scanDeviceMedia(context: Context) = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItemEntity>()

        // Scan Videos
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED
        )

        val videoUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        try {
            context.contentResolver.query(
                videoUri,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val wCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val hCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: cursor.getString(nameCol) ?: "Video $id"
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val contentUri = ContentUris.withAppendedId(videoUri, id).toString()
                    val duration = cursor.getLong(durCol)
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val width = if (wCol != -1) cursor.getInt(wCol) else 0
                    val height = if (hCol != -1) cursor.getInt(hCol) else 0
                    val dateAdded = cursor.getLong(dateCol) * 1000

                    val parentFile = if (path.isNotEmpty()) File(path).parentFile else null
                    val folderPath = parentFile?.absolutePath ?: "Internal Storage"
                    val folderName = parentFile?.name ?: "Videos"

                    val resLabel = if (height > 0) "${height}p" else null

                    items.add(
                        MediaItemEntity(
                            id = "video_$id",
                            title = title,
                            path = path,
                            uri = contentUri,
                            durationMs = duration,
                            sizeBytes = size,
                            mimeType = mime,
                            mediaType = "VIDEO",
                            resolution = resLabel,
                            width = width,
                            height = height,
                            dateAdded = dateAdded,
                            folderPath = folderPath,
                            folderName = folderName
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // Scan Audio
        val audioProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val audioUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        try {
            context.contentResolver.query(
                audioUri,
                audioProjection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Audio $id"
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val contentUri = ContentUris.withAppendedId(audioUri, id).toString()
                    val duration = cursor.getLong(durCol)
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "audio/mpeg"
                    val artist = if (artistCol != -1) cursor.getString(artistCol) else null
                    val album = if (albumCol != -1) cursor.getString(albumCol) else null
                    val dateAdded = cursor.getLong(dateCol) * 1000

                    val parentFile = if (path.isNotEmpty()) File(path).parentFile else null
                    val folderPath = parentFile?.absolutePath ?: "Internal Storage"
                    val folderName = parentFile?.name ?: "Music"

                    items.add(
                        MediaItemEntity(
                            id = "audio_$id",
                            title = title,
                            path = path,
                            uri = contentUri,
                            durationMs = duration,
                            sizeBytes = size,
                            mimeType = mime,
                            mediaType = "AUDIO",
                            resolution = null,
                            width = 0,
                            height = 0,
                            dateAdded = dateAdded,
                            folderPath = folderPath,
                            folderName = folderName,
                            artist = artist,
                            album = album
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        if (items.isNotEmpty()) {
            mediaDao.insertMediaItems(items)
        }
    }

    private fun MediaItemEntity.toModel(): MediaItemModel {
        return MediaItemModel(
            id = id,
            title = title,
            path = path,
            uri = uri,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            mimeType = mimeType,
            mediaType = if (mediaType == "AUDIO") MediaType.AUDIO else MediaType.VIDEO,
            resolution = resolution,
            width = width,
            height = height,
            dateAdded = dateAdded,
            isFavorite = isFavorite,
            playbackPositionMs = playbackPositionMs,
            lastPlayedTimestamp = lastPlayedTimestamp,
            folderPath = folderPath,
            folderName = folderName,
            artist = artist,
            album = album
        )
    }
}
