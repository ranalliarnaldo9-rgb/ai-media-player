package com.example.model

enum class MediaType {
    VIDEO,
    AUDIO
}

enum class AspectRatioMode(val label: String) {
    FIT("Fit Screen"),
    ZOOM("Zoom / Crop"),
    STRETCH("Stretch"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3")
}

enum class DecoderPreference(val label: String) {
    AUTO("Auto (Recommended)"),
    HARDWARE("Hardware Accelerated"),
    SOFTWARE("Software Fallback")
}

enum class PlaybackRepeatMode {
    OFF,
    ONE,
    ALL
}

enum class SortOption(val label: String) {
    DATE_DESC("Date (Newest First)"),
    DATE_ASC("Date (Oldest First)"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SIZE_DESC("Size (Largest First)"),
    SIZE_ASC("Size (Smallest First)"),
    DURATION_DESC("Duration (Longest First)")
}

data class MediaItemModel(
    val id: String,
    val title: String,
    val path: String,
    val uri: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val mimeType: String = "video/mp4",
    val mediaType: MediaType = MediaType.VIDEO,
    val resolution: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val lastPlayedTimestamp: Long? = null,
    val folderPath: String = "",
    val folderName: String = "",
    val artist: String? = null,
    val album: String? = null,
    val isWebStream: Boolean = false,
    val webEmbedUrl: String? = null
)

data class FolderGroup(
    val folderPath: String,
    val folderName: String,
    val mediaCount: Int,
    val totalSizeBytes: Long,
    val latestTimestamp: Long
)

data class StreamItem(
    val url: String,
    val title: String,
    val resolution: String = "",
    val bitrate: Long = 0L,
    val mimeType: String = "",
    val isHls: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val estimatedSizeBytes: Long = 0L
)

data class SubtitleCue(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

data class TrackInfo(
    val id: String,
    val label: String,
    val language: String? = null,
    val mimeType: String? = null,
    val isSelected: Boolean = false
)
