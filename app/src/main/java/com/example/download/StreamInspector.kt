package com.example.download

import android.media.MediaMetadataRetriever
import com.example.model.StreamItem
import com.example.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

sealed class StreamInspectionResult {
    data class Success(
        val originalUrl: String,
        val isHls: Boolean,
        val variants: List<StreamItem>
    ) : StreamInspectionResult()

    data class Error(val message: String) : StreamInspectionResult()
}

class StreamInspector(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {

    suspend fun inspectUrl(url: String): StreamInspectionResult = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return@withContext StreamInspectionResult.Error("Please enter a valid HTTP or HTTPS stream URL")
        }

        try {
            // Check if YouTube link
            val ytId = extractYouTubeVideoId(trimmed)
            if (ytId != null) {
                val embedUrl = "https://www.youtube.com/embed/$ytId?autoplay=1&playsinline=1&modestbranding=1&rel=0&fs=1"
                return@withContext StreamInspectionResult.Success(
                    originalUrl = trimmed,
                    isHls = false,
                    variants = listOf(
                        StreamItem(
                            url = embedUrl,
                            title = "YouTube Video ($ytId)",
                            resolution = "1080p FHD (Universal Stream)",
                            mimeType = "video/youtube",
                            isHls = false,
                            width = 1920,
                            height = 1080
                        ),
                        StreamItem(
                            url = embedUrl,
                            title = "YouTube Video ($ytId) - 720p",
                            resolution = "720p HD",
                            mimeType = "video/youtube",
                            isHls = false,
                            width = 1280,
                            height = 720
                        )
                    )
                )
            }

            // Check if URL ends with or contains m3u8
            val isHls = trimmed.contains(".m3u8", ignoreCase = true)
            if (isHls) {
                return@withContext parseHlsManifest(trimmed)
            } else {
                return@withContext probeProgressiveStream(trimmed)
            }
        } catch (e: Exception) {
            StreamInspectionResult.Error("Network error: ${e.localizedMessage ?: "Failed to inspect stream"}")
        }
    }

    private val browserUserAgent = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private fun parseHlsManifest(masterUrl: String): StreamInspectionResult {
        val request = Request.Builder()
            .url(masterUrl)
            .header("User-Agent", browserUserAgent)
            .header("Accept", "*/*")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return StreamInspectionResult.Error("Server returned HTTP ${response.code}: ${response.message}")
            }

            val body = response.body?.string() ?: ""
            if (!body.contains("#EXTM3U")) {
                return StreamInspectionResult.Error("Invalid HLS playlist manifest format")
            }

            // Check if this is a master playlist (contains STREAM-INF)
            if (body.contains("#EXT-X-STREAM-INF")) {
                val variants = mutableListOf<StreamItem>()
                val reader = BufferedReader(StringReader(body))
                var line: String? = reader.readLine()
                var currentBandwidth = 0L
                var currentResolution = ""
                var currentWidth = 0
                var currentHeight = 0

                val resolutionPattern = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)")
                val bandwidthPattern = Pattern.compile("BANDWIDTH=(\\d+)")

                while (line != null) {
                    val trimmedLine = line.trim()
                    if (trimmedLine.startsWith("#EXT-X-STREAM-INF")) {
                        // Extract resolution
                        val resMatcher = resolutionPattern.matcher(trimmedLine)
                        if (resMatcher.find()) {
                            val w = resMatcher.group(1)?.toIntOrNull() ?: 0
                            val h = resMatcher.group(2)?.toIntOrNull() ?: 0
                            currentWidth = w
                            currentHeight = h
                            currentResolution = formatResolutionLabel(w, h)
                        } else {
                            currentResolution = "Adaptive Track"
                        }

                        // Extract bandwidth
                        val bwMatcher = bandwidthPattern.matcher(trimmedLine)
                        if (bwMatcher.find()) {
                            currentBandwidth = bwMatcher.group(1)?.toLongOrNull() ?: 0L
                        }
                    } else if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                        // This is the URI line for the variant
                        val variantUrl = NetworkUtils.resolveUrl(masterUrl, trimmedLine)
                        val estimatedSize = if (currentBandwidth > 0) {
                            // Estimate for a typical 10-minute video
                            (currentBandwidth / 8) * 600
                        } else 0L

                        variants.add(
                            StreamItem(
                                url = variantUrl,
                                title = "HLS Variant - $currentResolution",
                                resolution = currentResolution,
                                bitrate = currentBandwidth,
                                mimeType = "application/x-mpegURL",
                                isHls = true,
                                width = currentWidth,
                                height = currentHeight,
                                estimatedSizeBytes = estimatedSize
                            )
                        )
                        currentBandwidth = 0L
                        currentResolution = ""
                    }
                    line = reader.readLine()
                }

                if (variants.isNotEmpty()) {
                    // Sort descending by height / bandwidth
                    val sorted = variants.sortedWith(
                        compareByDescending<StreamItem> { it.height }
                            .thenByDescending { it.bitrate }
                    )
                    return StreamInspectionResult.Success(masterUrl, isHls = true, variants = sorted)
                }
            }

            // Fallback: media playlist with single rendition
            return StreamInspectionResult.Success(
                masterUrl,
                isHls = true,
                variants = listOf(
                    StreamItem(
                        url = masterUrl,
                        title = "HLS Stream (Direct)",
                        resolution = "Live / Auto",
                        mimeType = "application/x-mpegURL",
                        isHls = true
                    )
                )
            )
        }
    }

    private fun probeProgressiveStream(url: String): StreamInspectionResult {
        // Send a HEAD request first to inspect Content-Length and Content-Type
        val headRequest = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", browserUserAgent)
            .header("Accept", "*/*")
            .build()

        var contentLength = -1L
        var contentType = "video/mp4"

        try {
            client.newCall(headRequest).execute().use { response ->
                if (response.isSuccessful) {
                    contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    contentType = response.header("Content-Type") ?: "video/mp4"
                } else {
                    // Server rejected HEAD (e.g. 403 or 405 Method Not Allowed), fallback to small GET
                    val getReq = Request.Builder()
                        .url(url)
                        .header("User-Agent", browserUserAgent)
                        .header("Range", "bytes=0-1024")
                        .build()
                    client.newCall(getReq).execute().use { getResp ->
                        val lengthStr = getResp.header("Content-Range")?.substringAfterLast("/")
                        contentLength = lengthStr?.toLongOrNull() ?: getResp.header("Content-Length")?.toLongOrNull() ?: -1L
                        contentType = getResp.header("Content-Type") ?: "video/mp4"
                    }
                }
            }
        } catch (_: Exception) {
            // Some servers reject HEAD or range requests; continue with derived metadata
        }

        var width = 0
        var height = 0
        val resolutionLabel = "Direct Download Stream"
        val rawTitle = url.substringAfterLast("/").substringBefore("?").ifEmpty { "download_media" }
        val title = rawTitle.replace("%20", " ")

        if (contentType.contains("text/html", ignoreCase = true)) {
            // Sniff HTML for direct video src or OpenGraph video
            try {
                val getReq = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .build()
                client.newCall(getReq).execute().use { htmlResp ->
                    val html = htmlResp.body?.string() ?: ""
                    val videoSrcPattern = Pattern.compile("<(?:video|source)[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                    val ogVideoPattern = Pattern.compile("<meta[^>]+property=[\"']og:video(?::url)?[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                    val vMatcher = videoSrcPattern.matcher(html)
                    val ogMatcher = ogVideoPattern.matcher(html)

                    val directUrl = when {
                        vMatcher.find() -> vMatcher.group(1)
                        ogMatcher.find() -> ogMatcher.group(1)
                        else -> null
                    }

                    if (!directUrl.isNullOrBlank()) {
                        val fullUrl = NetworkUtils.resolveUrl(url, directUrl)
                        return probeProgressiveStream(fullUrl)
                    }
                }
            } catch (_: Exception) {}

            val cleanTitle = url.substringAfterLast("/").substringBefore("?").ifEmpty { "Web Video Stream" }
            return StreamInspectionResult.Success(
                originalUrl = url,
                isHls = false,
                variants = listOf(
                    StreamItem(
                        url = url,
                        title = cleanTitle,
                        resolution = "Universal Web Stream",
                        mimeType = "video/web",
                        isHls = false
                    )
                )
            )
        }

        return StreamInspectionResult.Success(
            originalUrl = url,
            isHls = false,
            variants = listOf(
                StreamItem(
                    url = url,
                    title = title,
                    resolution = resolutionLabel,
                    mimeType = contentType,
                    isHls = false,
                    width = width,
                    height = height,
                    estimatedSizeBytes = if (contentLength > 0) contentLength else 0L
                )
            )
        )
    }

    private fun formatResolutionLabel(width: Int, height: Int): String {
        return when {
            height >= 2160 || width >= 3840 -> "4K UHD (${width}x${height})"
            height >= 1440 || width >= 2560 -> "2K QHD (${width}x${height})"
            height >= 1080 || width >= 1920 -> "1080p FHD (${width}x${height})"
            height >= 720 || width >= 1280 -> "720p HD (${width}x${height})"
            height >= 480 || width >= 854 -> "480p SD (${width}x${height})"
            height >= 360 || width >= 640 -> "360p (${width}x${height})"
            height > 0 -> "${height}p (${width}x${height})"
            else -> "Original"
        }
    }

    companion object {
        fun extractYouTubeVideoId(url: String): String? {
            val clean = url.trim()
            val pattern = "(?<=watch\\?v=|/videos/|embed/|youtu.be/|/v/|/e/|watch\\?feature=player_embedded&v=|/shorts/|\\?v=)[^#&?\\n]*"
            val compiledPattern = Pattern.compile(pattern)
            val matcher = compiledPattern.matcher(clean)
            if (matcher.find()) {
                val id = matcher.group()
                if (!id.isNullOrBlank()) return id
            }
            if (clean.length == 11 && clean.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
                return clean
            }
            return null
        }
    }
}
