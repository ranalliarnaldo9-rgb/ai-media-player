package com.example.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.example.model.AspectRatioMode
import com.example.model.MediaItemModel
import com.example.model.MediaType
import com.example.model.PlaybackRepeatMode
import com.example.model.TrackInfo
import com.example.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressPollingJob: Job? = null

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("NOVA-MediaPlayer/2.0 (Android)")
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(20000)
        .setAllowCrossProtocolRedirects(true)

    private val dataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)

    private val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(mediaSourceFactory)
        .setSeekForwardIncrementMs(10000)
        .setSeekBackIncrementMs(10000)
        .build()

    private val _currentMedia = MutableStateFlow<MediaItemModel?>(null)
    val currentMedia: StateFlow<MediaItemModel?> = _currentMedia.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _aspectRatioMode = MutableStateFlow(AspectRatioMode.FIT)
    val aspectRatioMode: StateFlow<AspectRatioMode> = _aspectRatioMode.asStateFlow()

    private val _volumeBoost = MutableStateFlow(1.0f)
    val volumeBoost: StateFlow<Float> = _volumeBoost.asStateFlow()

    private val _repeatMode = MutableStateFlow(PlaybackRepeatMode.OFF)
    val repeatMode: StateFlow<PlaybackRepeatMode> = _repeatMode.asStateFlow()

    data class ABLoop(val startMs: Long, val endMs: Long? = null)
    private val _abLoop = MutableStateFlow<ABLoop?>(null)
    val abLoop: StateFlow<ABLoop?> = _abLoop.asStateFlow()

    interface WebPlayerController {
        fun webPlay()
        fun webPause()
        fun webSeekTo(seconds: Float)
        fun webSetPlaybackRate(rate: Float)
    }

    var webPlayerController: WebPlayerController? = null

    private val _audioTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<TrackInfo>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val subtitleTracks: StateFlow<List<TrackInfo>> = _subtitleTracks.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) startPollingProgress() else stopPollingProgress()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = exoPlayer.duration.coerceAtLeast(0L)
                    _errorMessage.value = null
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                extractTracks(tracks)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val currentUri = _currentMedia.value?.uri ?: ""
                val isYouTube = currentUri.contains("youtube.com", ignoreCase = true) || currentUri.contains("youtu.be", ignoreCase = true)
                val isSocialWeb = currentUri.contains("instagram.com", ignoreCase = true) || currentUri.contains("tiktok.com", ignoreCase = true) || currentUri.contains("facebook.com", ignoreCase = true)

                val friendlyMessage = when {
                    isYouTube -> "YouTube Webpage Detected!\nYouTube pages cannot be played directly because they are HTML web pages, not direct video files. Please paste a direct .mp4 or .m3u8 stream URL."
                    isSocialWeb -> "Social Media Webpage Detected!\nWebpages cannot be played directly. Please paste a direct .mp4 or HLS (.m3u8) video URL."
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Direct stream file format unsupported. The link may point to an HTML page instead of a raw video file."
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network connection failed. Please check internet connection."
                    else -> "Source Error: The URL is either not a direct video file or the stream has expired."
                }
                _errorMessage.value = friendlyMessage
                _isPlaying.value = false
            }
        })
    }

    fun playMedia(media: MediaItemModel, startPositionMs: Long = 0L) {
        _currentMedia.value = media
        _errorMessage.value = null

        if (media.isWebStream) {
            exoPlayer.stop()
            _isPlaying.value = true
            return
        }

        val uri = if (media.uri.startsWith("/")) {
            Uri.fromFile(java.io.File(media.uri))
        } else {
            Uri.parse(media.uri)
        }
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(media.id)
            .apply {
                if (media.uri.contains(".m3u8", ignoreCase = true)) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (startPositionMs > 0) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.play()
    }

    fun playStreamUrl(url: String, title: String? = null) {
        val trimmed = url.trim()
        val ytId = com.example.download.StreamInspector.extractYouTubeVideoId(trimmed)
        val isYouTube = ytId != null
        val isExplicitDirect = trimmed.contains(".mp4", ignoreCase = true) ||
                trimmed.contains(".m3u8", ignoreCase = true) ||
                trimmed.contains(".webm", ignoreCase = true) ||
                trimmed.contains(".mkv", ignoreCase = true) ||
                trimmed.contains(".ts", ignoreCase = true)

        val isWebStream = isYouTube || !isExplicitDirect

        val cleanTitle = when {
            isYouTube -> "YouTube Video ($ytId)"
            !title.isNullOrBlank() -> title
            else -> trimmed.substringAfterLast("/").substringBefore("?").ifEmpty { "Web Video Stream" }
        }

        val embedUrl = when {
            isYouTube -> "https://www.youtube.com/embed/$ytId?autoplay=1&playsinline=1&modestbranding=1&rel=0&fs=1"
            else -> trimmed
        }

        val isHls = trimmed.contains(".m3u8", ignoreCase = true)

        val streamMedia = MediaItemModel(
            id = "stream_${trimmed.hashCode()}",
            title = cleanTitle,
            path = trimmed,
            uri = trimmed,
            mimeType = when {
                isYouTube -> "video/youtube"
                isHls -> MimeTypes.APPLICATION_M3U8
                isWebStream -> "video/web"
                else -> "video/mp4"
            },
            mediaType = MediaType.VIDEO,
            isWebStream = isWebStream,
            webEmbedUrl = embedUrl
        )
        playMedia(streamMedia)
    }

    fun setWebPlaybackState(playing: Boolean, buffering: Boolean) {
        _isPlaying.value = playing
        _isBuffering.value = buffering
    }

    fun setWebProgress(currentSec: Float, durationSec: Float) {
        val posMs = (currentSec * 1000).toLong()
        _currentPositionMs.value = posMs
        if (durationSec > 0) {
            _durationMs.value = (durationSec * 1000).toLong()
        }

        // Check A/B loop
        val loop = _abLoop.value
        if (loop != null && loop.endMs != null && posMs >= loop.endMs) {
            seekTo(loop.startMs)
        }
    }

    fun toggleABLoop(): String {
        val current = _abLoop.value
        val pos = _currentPositionMs.value
        return if (current == null) {
            _abLoop.value = ABLoop(startMs = pos, endMs = null)
            "A Point Set: ${FormatUtils.formatDuration(pos)}"
        } else if (current.endMs == null) {
            if (pos > current.startMs) {
                _abLoop.value = current.copy(endMs = pos)
                "A/B Loop Active: ${FormatUtils.formatDuration(current.startMs)} ➔ ${FormatUtils.formatDuration(pos)}"
            } else {
                _abLoop.value = null
                "A/B Loop Reset"
            }
        } else {
            _abLoop.value = null
            "A/B Loop Disabled"
        }
    }

    fun togglePlayPause() {
        val current = _currentMedia.value
        if (current?.isWebStream == true) {
            if (_isPlaying.value) {
                webPlayerController?.webPause()
                _isPlaying.value = false
            } else {
                webPlayerController?.webPlay()
                _isPlaying.value = true
            }
        } else {
            if (exoPlayer.isPlaying) {
                exoPlayer.pause()
            } else {
                exoPlayer.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val current = _currentMedia.value
        val clamped = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(0L))
        _currentPositionMs.value = clamped

        if (current?.isWebStream == true) {
            webPlayerController?.webSeekTo(clamped / 1000f)
        } else {
            exoPlayer.seekTo(clamped)
        }
    }

    fun seekForward(deltaMs: Long = 10000L) {
        seekTo(_currentPositionMs.value + deltaMs)
    }

    fun seekBackward(deltaMs: Long = 10000L) {
        seekTo((_currentPositionMs.value - deltaMs).coerceAtLeast(0L))
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        val current = _currentMedia.value
        if (current?.isWebStream == true) {
            webPlayerController?.webSetPlaybackRate(speed)
        } else {
            exoPlayer.playbackParameters = PlaybackParameters(speed)
        }
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _aspectRatioMode.value = mode
    }

    fun setVolumeMultiplier(multiplier: Float) {
        val clamped = multiplier.coerceIn(1.0f, 2.0f)
        _volumeBoost.value = clamped
        exoPlayer.volume = clamped
    }

    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ALL
            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.OFF
        }
        _repeatMode.value = next
        exoPlayer.repeatMode = when (next) {
            PlaybackRepeatMode.OFF -> Player.REPEAT_MODE_OFF
            PlaybackRepeatMode.ALL -> Player.REPEAT_MODE_ALL
            PlaybackRepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun selectSubtitleTrack(trackId: String?) {
        val parameters = exoPlayer.trackSelectionParameters.buildUpon()
        if (trackId == null) {
            parameters.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            parameters.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        }
        exoPlayer.trackSelectionParameters = parameters.build()
    }

    fun selectAudioTrack(trackId: String) {
        // Track override logic
    }

    private fun extractTracks(tracks: Tracks) {
        val audios = mutableListOf<TrackInfo>()
        val subs = mutableListOf<TrackInfo>()

        for (group in tracks.groups) {
            val type = group.type
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val isSelected = group.isTrackSelected(i)
                val label = format.label ?: format.language ?: "Track ${i + 1}"

                if (type == C.TRACK_TYPE_AUDIO) {
                    audios.add(
                        TrackInfo(
                            id = format.id ?: "$i",
                            label = "$label (${format.sampleMimeType ?: "audio"})",
                            language = format.language,
                            mimeType = format.sampleMimeType,
                            isSelected = isSelected
                        )
                    )
                } else if (type == C.TRACK_TYPE_TEXT) {
                    subs.add(
                        TrackInfo(
                            id = format.id ?: "$i",
                            label = label,
                            language = format.language,
                            mimeType = format.sampleMimeType,
                            isSelected = isSelected
                        )
                    )
                }
            }
        }

        _audioTracks.value = audios
        _subtitleTracks.value = subs
    }

    private fun startPollingProgress() {
        stopPollingProgress()
        progressPollingJob = scope.launch {
            while (isActive) {
                val pos = exoPlayer.currentPosition
                _currentPositionMs.value = pos
                _durationMs.value = exoPlayer.duration.coerceAtLeast(0L)

                val loop = _abLoop.value
                if (loop != null && loop.endMs != null && pos >= loop.endMs) {
                    seekTo(loop.startMs)
                }

                delay(250)
            }
        }
    }

    private fun stopPollingProgress() {
        progressPollingJob?.cancel()
        progressPollingJob = null
    }

    fun stopPlayback() {
        stopPollingProgress()
        exoPlayer.stop()
        _isPlaying.value = false
        _currentMedia.value = null
    }

    fun release() {
        stopPollingProgress()
        exoPlayer.release()
    }

    companion object {
        @Volatile
        private var INSTANCE: PlaybackManager? = null

        fun getPlaybackManager(context: Context): PlaybackManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PlaybackManager(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
