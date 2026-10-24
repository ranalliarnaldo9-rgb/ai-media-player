package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val path: String,
    val uri: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val mediaType: String, // "VIDEO" or "AUDIO"
    val resolution: String?,
    val width: Int,
    val height: Int,
    val dateAdded: Long,
    val isFavorite: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val lastPlayedTimestamp: Long? = null,
    val folderPath: String,
    val folderName: String,
    val artist: String? = null,
    val album: String? = null
)

@Entity(tableName = "downloads")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val resolution: String,
    val mimeType: String = "video/mp4",
    val localUri: String? = null,
    val tempFilePath: String? = null,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: String = "QUEUED", // QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val isResumable: Boolean = true,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val folderPath: String,
    val folderName: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_media_cross_ref", primaryKeys = ["playlistId", "mediaId"])
data class PlaylistMediaCrossRef(
    val playlistId: String,
    val mediaId: String,
    val addedOrder: Int = 0
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val mediaId: String,
    val mediaTitle: String,
    val mediaUri: String,
    val timestampMs: Long,
    val label: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
