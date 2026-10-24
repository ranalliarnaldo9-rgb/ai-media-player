package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.BookmarkEntity
import com.example.database.DownloadTaskEntity
import com.example.database.NovaDatabase
import com.example.download.NovaDownloadService
import com.example.download.StreamInspectionResult
import com.example.download.StreamInspector
import com.example.model.AspectRatioMode
import com.example.model.MediaItemModel
import com.example.model.MediaType
import com.example.model.SortOption
import com.example.model.StreamItem
import com.example.playback.PlaybackManager
import com.example.repository.MediaRepository
import com.example.settings.SettingsManager
import com.example.util.FormatUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class DownloadFilter {
    ALL,
    ACTIVE,
    COMPLETED
}

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val database = NovaDatabase.getDatabase(application)
    private val mediaRepository = MediaRepository(database.mediaDao())
    val playbackManager = PlaybackManager.getPlaybackManager(application)
    val settingsManager = SettingsManager.getSettingsManager(application)
    private val streamInspector = StreamInspector()

    // Media Flows
    val allVideos = mediaRepository.allVideos.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allAudio = mediaRepository.allAudio.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val continueWatching = mediaRepository.continueWatching.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val favorites = mediaRepository.favorites.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allBookmarks = database.bookmarkDao().fetchAllBookmarks().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Downloads
    val allDownloads = database.downloadDao().getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _downloadFilter = MutableStateFlow(DownloadFilter.ALL)
    val downloadFilter = _downloadFilter.asStateFlow()

    val filteredDownloads = combine(allDownloads, _downloadFilter) { list, filter ->
        when (filter) {
            DownloadFilter.ALL -> list
            DownloadFilter.ACTIVE -> list.filter { it.status in listOf("DOWNLOADING", "QUEUED", "PAUSED", "CANCELED", "FAILED") }
            DownloadFilter.COMPLETED -> list.filter { it.status == "COMPLETED" }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Stream Dialog & Resolution Sheet State
    private val _showStreamDialog = MutableStateFlow(false)
    val showStreamDialog = _showStreamDialog.asStateFlow()

    private val _streamUrlInput = MutableStateFlow("")
    val streamUrlInput = _streamUrlInput.asStateFlow()

    private val _isInspectingStream = MutableStateFlow(false)
    val isInspectingStream = _isInspectingStream.asStateFlow()

    private val _inspectionResult = MutableStateFlow<StreamInspectionResult?>(null)
    val inspectionResult = _inspectionResult.asStateFlow()

    private val _showResolutionSheet = MutableStateFlow(false)
    val showResolutionSheet = _showResolutionSheet.asStateFlow()

    // Search & Sort State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _videoSortOption = MutableStateFlow(SortOption.DATE_DESC)
    val videoSortOption = _videoSortOption.asStateFlow()

    init {
        // Initial scan of device media
        viewModelScope.launch {
            mediaRepository.scanDeviceMedia(getApplication())
        }
    }

    fun rescanMedia() {
        viewModelScope.launch {
            mediaRepository.scanDeviceMedia(getApplication())
        }
    }

    fun setDownloadFilter(filter: DownloadFilter) {
        _downloadFilter.value = filter
    }

    fun openStreamDialog() {
        _showStreamDialog.value = true
        _inspectionResult.value = null
    }

    fun closeStreamDialog() {
        _showStreamDialog.value = false
        _inspectionResult.value = null
    }

    fun updateStreamUrl(url: String) {
        _streamUrlInput.value = url
    }

    fun inspectAndPrepareDownload(url: String) {
        viewModelScope.launch {
            _isInspectingStream.value = true
            val result = streamInspector.inspectUrl(url)
            _isInspectingStream.value = false
            _inspectionResult.value = result

            if (result is StreamInspectionResult.Success) {
                _showStreamDialog.value = false
                _showResolutionSheet.value = true
            }
        }
    }

    fun closeResolutionSheet() {
        _showResolutionSheet.value = false
    }

    fun startDownload(variant: StreamItem) {
        val taskId = "dl_${UUID.randomUUID().toString().take(8)}"
        NovaDownloadService.enqueueDownloadService(
            context = getApplication(),
            taskId = taskId,
            url = variant.url,
            title = variant.title.ifEmpty { "Online Video" },
            resolution = variant.resolution.ifEmpty { "Original" }
        )
        closeResolutionSheet()
    }

    fun startDirectDownload(url: String, title: String? = null, resolution: String = "Direct Stream") {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            return
        }
        val safeTitle = title?.ifBlank { null }
            ?: cleanUrl.substringAfterLast("/").substringBefore("?").replace("%20", " ").ifEmpty { "Media_Download" }
        val taskId = "dl_${UUID.randomUUID().toString().take(8)}"
        NovaDownloadService.enqueueDownloadService(
            context = getApplication(),
            taskId = taskId,
            url = cleanUrl,
            title = safeTitle,
            resolution = resolution
        )
        closeStreamDialog()
        closeResolutionSheet()
    }

    fun pauseDownload(taskId: String) {
        NovaDownloadService.pauseDownloadService(getApplication(), taskId)
    }

    fun resumeDownload(task: DownloadTaskEntity) {
        NovaDownloadService.resumeDownloadService(
            context = getApplication(),
            taskId = task.id,
            url = task.url,
            title = task.title,
            resolution = task.resolution
        )
    }

    fun cancelDownload(taskId: String) {
        NovaDownloadService.cancelDownloadService(getApplication(), taskId)
    }

    fun retryDownload(task: DownloadTaskEntity) {
        NovaDownloadService.enqueueDownloadService(
            context = getApplication(),
            taskId = task.id,
            url = task.url,
            title = task.title,
            resolution = task.resolution
        )
    }

    fun retryAllCanceled() {
        viewModelScope.launch {
            val list = database.downloadDao().getAllDownloads().first()
            list.filter { it.status == "CANCELED" || it.status == "FAILED" }.forEach { task ->
                retryDownload(task)
            }
        }
    }

    fun completeAllCanceled() {
        viewModelScope.launch {
            val list = database.downloadDao().getAllDownloads().first()
            list.filter { it.status == "CANCELED" || it.status == "FAILED" || it.status == "PAUSED" }.forEach { task ->
                retryDownload(task)
            }
        }
    }

    fun markDownloadCompleted(task: DownloadTaskEntity) {
        viewModelScope.launch {
            retryDownload(task)
        }
    }

    fun deleteDownload(task: DownloadTaskEntity) {
        viewModelScope.launch {
            database.downloadDao().deleteById(task.id)
            task.localUri?.let { path ->
                try {
                    val file = java.io.File(path)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
            task.tempFilePath?.let { path ->
                try {
                    val file = java.io.File(path)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
        }
    }

    fun startPlayback(media: MediaItemModel) {
        playbackManager.playMedia(media)
        viewModelScope.launch {
            mediaRepository.savePlaybackPosition(media.id, 0L)
        }
    }

    fun playStreamDirect(url: String) {
        playbackManager.playStreamUrl(url)
        closeStreamDialog()
    }

    fun toggleFavorite(media: MediaItemModel) {
        viewModelScope.launch {
            mediaRepository.updateFavoriteStatus(media.id, media.isFavorite)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setVideoSort(sort: SortOption) {
        _videoSortOption.value = sort
    }

    fun recordNewBookmark(media: MediaItemModel, timestampMs: Long, label: String, note: String = "") {
        viewModelScope.launch {
            val safeLabel = label.ifBlank { "Scene at ${FormatUtils.formatDuration(timestampMs)}" }
            val bookmark = BookmarkEntity(
                id = "bm_${UUID.randomUUID().toString().take(8)}",
                mediaId = media.id,
                mediaTitle = media.title,
                mediaUri = media.uri,
                timestampMs = timestampMs,
                label = safeLabel,
                note = note
            )
            database.bookmarkDao().insertNewBookmark(bookmark)
        }
    }

    fun deleteBookmarkRecord(bookmarkId: String) {
        viewModelScope.launch {
            database.bookmarkDao().removeBookmarkRecord(bookmarkId)
        }
    }

    fun playMediaFromBookmark(media: MediaItemModel, positionMs: Long) {
        playbackManager.playMedia(media, startPositionMs = positionMs)
    }
}
