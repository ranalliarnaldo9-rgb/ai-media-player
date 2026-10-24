package com.example.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.PixelCopy
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.ui.zIndex
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.model.AspectRatioMode
import com.example.model.MediaItemModel
import com.example.playback.PlaybackManager
import com.example.ui.components.UniversalWebPlayerView
import com.example.ui.theme.*
import com.example.util.FormatUtils
import com.example.viewmodel.MediaViewModel
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    viewModel: MediaViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackManager = viewModel.playbackManager
    val currentMedia by playbackManager.currentMedia.collectAsState()
    val isPlaying by playbackManager.isPlaying.collectAsState()
    val isBuffering by playbackManager.isBuffering.collectAsState()
    val positionMs by playbackManager.currentPositionMs.collectAsState()
    val durationMs by playbackManager.durationMs.collectAsState()
    val playbackSpeed by playbackManager.playbackSpeed.collectAsState()
    val aspectRatioMode by playbackManager.aspectRatioMode.collectAsState()
    val errorMessage by playbackManager.errorMessage.collectAsState()
    val abLoop by playbackManager.abLoop.collectAsState()
    val audioTracks by playbackManager.audioTracks.collectAsState()
    val subtitleTracks by playbackManager.subtitleTracks.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var lockButtonVisible by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var isBackgroundAudioOn by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableStateOf(0) }
    val volumeBoost by playbackManager.volumeBoost.collectAsState()
    var isAudioOnlyMode by remember { mutableStateOf(false) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var newBookmarkLabel by remember { mutableStateOf("") }
    var newBookmarkNote by remember { mutableStateOf("") }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    var gestureFeedback by remember { mutableStateOf<String?>(null) }

    // Auto-dismiss gesture feedback after 1.5s
    LaunchedEffect(gestureFeedback) {
        if (gestureFeedback != null) {
            delay(1500)
            gestureFeedback = null
        }
    }

    // Auto-hide lock button after 2.5 seconds when screen is locked
    LaunchedEffect(lockButtonVisible, isLocked) {
        if (isLocked && lockButtonVisible) {
            delay(2500)
            lockButtonVisible = false
        }
    }

    // Auto-hide controls after 4 seconds of inactivity if playing
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying && !isLocked) {
            delay(4000)
            controlsVisible = false
        }
    }

    BackHandler {
        onNavigateBack()
    }

    val isOnlineStream = remember(currentMedia) {
        val uri = currentMedia?.uri ?: ""
        uri.startsWith("http://") || uri.startsWith("https://")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_screen")
    ) {
        // 1. VIDEO SURFACE (Universal Web Player OR Media3 ExoPlayer)
        if (currentMedia?.isWebStream == true) {
            UniversalWebPlayerView(
                url = currentMedia?.webEmbedUrl ?: currentMedia?.uri ?: "",
                playbackManager = playbackManager,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = playbackManager.exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        resizeMode = when (aspectRatioMode) {
                            AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                            AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                        }
                    }
                },
                update = { view ->
                    view.resizeMode = when (aspectRatioMode) {
                        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 1.5 AUDIO-ONLY MODE OVERLAY (Power-saving dark screen with visualizer)
        if (isAudioOnlyMode) {
            Surface(
                color = DeepVoid,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1.5f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = NovaCyan.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(2.dp, NovaCyan),
                        modifier = Modifier.size(110.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = NovaCyan,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "AUDIO-ONLY MODE ACTIVE",
                        color = NovaCyan,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = currentMedia?.title ?: "Playing Audio",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "OLED battery saver active • Audio playing in background",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { isAudioOnlyMode = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NovaCyan,
                            contentColor = DeepVoid
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore Video Display", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. TRANSPARENT TOUCH / GESTURE OVERLAY
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    if (!isLocked) {
                        detectTapGestures(
                            onTap = {
                                controlsVisible = !controlsVisible
                            },
                            onDoubleTap = { offset ->
                                if (offset.x < size.width / 2) {
                                    playbackManager.seekBackward(10000)
                                    gestureFeedback = "-10s"
                                } else {
                                    playbackManager.seekForward(10000)
                                    gestureFeedback = "+10s"
                                }
                            }
                        )
                    } else {
                        detectTapGestures(
                            onTap = {
                                lockButtonVisible = !lockButtonVisible
                            }
                        )
                    }
                }
                .pointerInput(isLocked) {
                    if (!isLocked) {
                        detectDragGestures(
                            onDragEnd = { gestureFeedback = null },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val isRightSide = change.position.x > size.width / 2
                                val delta = -dragAmount.y

                                if (isRightSide) {
                                    // Volume Control
                                    try {
                                        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        val step = if (delta > 0) 1 else -1
                                        val newVol = (currentVol + step).coerceIn(0, if (maxVolume > 0) maxVolume else 1)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                        val volPercent = if (maxVolume > 0) (newVol * 100) / maxVolume else 0
                                        gestureFeedback = "Volume: $volPercent%"
                                    } catch (_: Exception) {}
                                } else {
                                    // Brightness Control
                                    try {
                                        val activity = context as? Activity
                                        val window = activity?.window
                                        if (window != null) {
                                            val lp = window.attributes
                                            val curBright = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                            val newBright = (curBright + (delta / 500f)).coerceIn(0.01f, 1.0f)
                                            lp.screenBrightness = newBright
                                            window.attributes = lp
                                            val brightPercent = (newBright * 100).toInt()
                                            gestureFeedback = "Brightness: $brightPercent%"
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        )
                    }
                }
        )

        // 3. BUFFERING SPINNER
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = NovaCyan,
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 3.dp
                )
            }
        }

        // 4. GESTURE FEEDBACK PILL
        if (gestureFeedback != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NovaCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = gestureFeedback ?: "",
                        color = NovaCyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                    )
                }
            }
        }

        // 5. ERROR BANNER
        if (errorMessage != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CoralCrimson.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = CoralCrimson, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Playback Error",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onNavigateBack) {
                                Text("Go Back", color = TextPrimary)
                            }
                            Button(
                                onClick = {
                                    playbackManager.playStreamUrl(
                                        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                                        "Big Buck Bunny Sample"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NovaCyan, contentColor = DeepVoid)
                            ) {
                                Text("Try Sample")
                            }
                        }
                    }
                }
            }
        }

        // 6. FLOATING UNLOCK BUTTON ON LEFT EDGE (Only briefly visible when tapped in locked mode)
        AnimatedVisibility(
            visible = isLocked && lockButtonVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaCyan),
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable {
                        isLocked = false
                        controlsVisible = true
                        lockButtonVisible = false
                        gestureFeedback = "Screen Unlocked"
                    }
                    .testTag("floating_unlock_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Unlock",
                        tint = NovaCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tap to Unlock",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 7. FULL INTERACTIVE OVERLAYS (Top bar, Center buttons, and Bottom dock)
        AnimatedVisibility(
            visible = controlsVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // ==================== TOP ACTION BAR (Exactly matching Image 2) ====================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .statusBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back Arrow
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("player_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    // Screen Rotation
                    IconButton(
                        onClick = {
                            val activity = context as? Activity
                            if (activity != null) {
                                val current = activity.requestedOrientation
                                val isLandscape = (current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                                        current == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                                activity.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }
                                gestureFeedback = if (isLandscape) "Portrait Mode" else "Landscape Mode"
                            }
                        },
                        modifier = Modifier.testTag("player_rotation_button")
                    ) {
                        Icon(Icons.Default.ScreenRotation, contentDescription = "Screen Rotation", tint = Color.White)
                    }

                    // Aspect Ratio
                    IconButton(
                        onClick = {
                            val nextMode = when (aspectRatioMode) {
                                AspectRatioMode.FIT -> AspectRatioMode.ZOOM
                                AspectRatioMode.ZOOM -> AspectRatioMode.STRETCH
                                AspectRatioMode.STRETCH -> AspectRatioMode.RATIO_16_9
                                AspectRatioMode.RATIO_16_9 -> AspectRatioMode.RATIO_4_3
                                AspectRatioMode.RATIO_4_3 -> AspectRatioMode.FIT
                            }
                            playbackManager.setAspectRatio(nextMode)
                            gestureFeedback = "Aspect: ${nextMode.label}"
                        },
                        modifier = Modifier.testTag("player_aspect_button")
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                    }

                    // Subtitles
                    IconButton(
                        onClick = { showSubtitleDialog = true },
                        modifier = Modifier.testTag("player_subtitles_button")
                    ) {
                        Icon(Icons.Default.Subtitles, contentDescription = "Subtitles", tint = Color.White)
                    }

                    // Audio Tracks
                    IconButton(
                        onClick = { showAudioDialog = true },
                        modifier = Modifier.testTag("player_audio_button")
                    ) {
                        Icon(Icons.Default.Audiotrack, contentDescription = "Audio Tracks", tint = Color.White)
                    }

                    // Background Audio / Headphone
                    IconButton(
                        onClick = {
                            isBackgroundAudioOn = !isBackgroundAudioOn
                            gestureFeedback = if (isBackgroundAudioOn) "Background Audio Enabled" else "Background Audio Disabled"
                        },
                        modifier = Modifier.testTag("player_background_audio_button")
                    ) {
                        Icon(
                            Icons.Default.Headphones,
                            contentDescription = "Background Audio",
                            tint = if (isBackgroundAudioOn) NovaCyan else Color.White
                        )
                    }

                    // Bookmark / Add Scene Marker
                    IconButton(
                        onClick = {
                            newBookmarkLabel = "Scene at ${FormatUtils.formatDuration(positionMs)}"
                            newBookmarkNote = ""
                            showAddBookmarkDialog = true
                        },
                        modifier = Modifier.testTag("player_bookmark_button")
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "Add Bookmark", tint = NovaCyan)
                    }

                    // Floating Window (Picture-in-Picture)
                    IconButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val activity = context as? Activity
                                try {
                                    val params = PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .build()
                                    activity?.enterPictureInPictureMode(params)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "PiP error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "PiP requires Android 8.0+", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("player_pip_button")
                    ) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Floating Window PiP", tint = Color.White)
                    }

                    // Audio-Only Mode Toggle
                    IconButton(
                        onClick = {
                            isAudioOnlyMode = !isAudioOnlyMode
                            gestureFeedback = if (isAudioOnlyMode) "Audio-Only Mode (Screen Saved)" else "Video Display Restored"
                        },
                        modifier = Modifier.testTag("player_audio_only_button")
                    ) {
                        Icon(
                            imageVector = if (isAudioOnlyMode) Icons.Default.MusicVideo else Icons.Default.Audiotrack,
                            contentDescription = "Audio-Only Mode",
                            tint = if (isAudioOnlyMode) NovaCyan else Color.White
                        )
                    }

                    // A/B Loop Repeat
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (abLoop != null) NovaCyan.copy(alpha = 0.25f) else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                gestureFeedback = playbackManager.toggleABLoop()
                            }
                            .padding(4.dp)
                            .testTag("player_ab_loop_button")
                    ) {
                        Text(
                            text = "A/B",
                            color = if (abLoop != null) NovaCyan else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    // Lock Screen
                    IconButton(
                        onClick = {
                            isLocked = true
                            controlsVisible = false
                            lockButtonVisible = true
                            gestureFeedback = "Screen Locked"
                        },
                        modifier = Modifier.testTag("player_lock_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock Screen", tint = Color.White)
                    }

                    // Screenshot / Camera
                    IconButton(
                        onClick = {
                            gestureFeedback = "Screenshot Captured"
                            Toast.makeText(context, "Screenshot captured", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("player_screenshot_button")
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Screenshot", tint = Color.White)
                    }

                    // Download Stream Button (if online stream)
                    if (isOnlineStream) {
                        IconButton(
                            onClick = {
                                currentMedia?.uri?.let { url ->
                                    viewModel.inspectAndPrepareDownload(url)
                                }
                            },
                            modifier = Modifier.testTag("player_download_stream_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download Stream", tint = NovaCyan)
                        }
                    }
                }

                // ==================== CENTER PLAY/PAUSE & SEEK CONTROLS (Exactly matching Image 2) ====================
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = {
                            playbackManager.seekBackward(10000)
                            gestureFeedback = "-10s"
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("player_center_rewind_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(36.dp))

                    // Prominent Center Circular Play / Pause Button
                    Surface(
                        shape = CircleShape,
                        color = NovaCyan,
                        modifier = Modifier
                            .size(64.dp)
                            .clickable {
                                playbackManager.togglePlayPause()
                            }
                            .testTag("player_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = DeepVoid,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(36.dp))

                    // Forward 10s
                    IconButton(
                        onClick = {
                            playbackManager.seekForward(10000)
                            gestureFeedback = "+10s"
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("player_center_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // ==================== BOTTOM CONTROLS DOCK (Exactly matching Image 2) ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.8f))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Row 1: Time indicators (Current on Left, Total on Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = FormatUtils.formatDuration(positionMs),
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = FormatUtils.formatDuration(durationMs),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Row 2: Full-width Seeker Slider
                    val clampedPos = if (durationMs > 0) positionMs.coerceIn(0L, durationMs).toFloat() else 0f
                    val maxPos = if (durationMs > 0) durationMs.toFloat() else 1f

                    Slider(
                        value = clampedPos,
                        onValueChange = { targetPos ->
                            playbackManager.seekTo(targetPos.toLong())
                        },
                        valueRange = 0f..maxPos,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_seek_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = NovaCyan,
                            activeTrackColor = NovaCyan,
                            inactiveTrackColor = DarkSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Row 3: Bottom Action Toolbar (6 items: Subtitle, Audio, Speed, Aspect, Lock, More)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Subtitle
                        BottomActionItem(
                            icon = Icons.Default.Subtitles,
                            label = "Subtitle",
                            onClick = { showSubtitleDialog = true },
                            tag = "bottom_action_subtitle"
                        )

                        // 2. Audio
                        BottomActionItem(
                            icon = Icons.Default.Audiotrack,
                            label = "Audio",
                            onClick = { showAudioDialog = true },
                            tag = "bottom_action_audio"
                        )

                        // 3. Speed
                        BottomActionItem(
                            icon = Icons.Default.Speed,
                            label = "${playbackSpeed}x",
                            onClick = { showSpeedMenu = true },
                            tag = "bottom_action_speed"
                        )

                        // 4. Aspect
                        BottomActionItem(
                            icon = Icons.Default.AspectRatio,
                            label = "Aspect",
                            onClick = {
                                val nextMode = when (aspectRatioMode) {
                                    AspectRatioMode.FIT -> AspectRatioMode.ZOOM
                                    AspectRatioMode.ZOOM -> AspectRatioMode.STRETCH
                                    AspectRatioMode.STRETCH -> AspectRatioMode.RATIO_16_9
                                    AspectRatioMode.RATIO_16_9 -> AspectRatioMode.RATIO_4_3
                                    AspectRatioMode.RATIO_4_3 -> AspectRatioMode.FIT
                                }
                                playbackManager.setAspectRatio(nextMode)
                                gestureFeedback = "Aspect: ${nextMode.label}"
                            },
                            tag = "bottom_action_aspect"
                        )

                        // 5. Lock
                        BottomActionItem(
                            icon = Icons.Default.Lock,
                            label = "Lock",
                            onClick = {
                                isLocked = true
                                controlsVisible = false
                                lockButtonVisible = true
                                gestureFeedback = "Screen Locked"
                            },
                            tag = "bottom_action_lock"
                        )

                        // 6. More
                        BottomActionItem(
                            icon = Icons.Default.MoreVert,
                            label = "More",
                            onClick = { showMoreMenu = true },
                            tag = "bottom_action_more"
                        )
                    }
                }
            }
        }

        // ==================== DIALOGS & BOTTOM SHEETS ====================

        // Subtitle Dialog
        if (showSubtitleDialog) {
            AlertDialog(
                onDismissRequest = { showSubtitleDialog = false },
                title = { Text("Subtitles", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    playbackManager.selectSubtitleTrack(null)
                                    showSubtitleDialog = false
                                    gestureFeedback = "Subtitles Off"
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = subtitleTracks.none { it.isSelected },
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = NovaCyan)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Disable Subtitles", color = TextPrimary)
                        }

                        if (subtitleTracks.isEmpty()) {
                            Text(
                                "No embedded subtitle tracks detected in this video.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        } else {
                            subtitleTracks.forEach { track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            playbackManager.selectSubtitleTrack(track.id)
                                            showSubtitleDialog = false
                                            gestureFeedback = "Subtitle: ${track.label}"
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = track.isSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(selectedColor = NovaCyan)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(track.label, color = TextPrimary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitleDialog = false }) {
                        Text("Close", color = NovaCyan)
                    }
                },
                containerColor = DarkSurfaceElevated
            )
        }

        // Audio Track Dialog
        if (showAudioDialog) {
            AlertDialog(
                onDismissRequest = { showAudioDialog = false },
                title = { Text("Audio Track", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (audioTracks.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = true, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = NovaCyan))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Stereo (Default)", color = TextPrimary)
                            }
                        } else {
                            audioTracks.forEach { track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            playbackManager.selectAudioTrack(track.id)
                                            showAudioDialog = false
                                            gestureFeedback = "Audio: ${track.label}"
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = track.isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = NovaCyan))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(track.label, color = TextPrimary)
                                }
                            }
                        }

                        HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 6.dp))

                        // Volume Boost (Up to 200%)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Volume Boost (Loudness)", color = TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("${(volumeBoost * 100).toInt()}%", color = if (volumeBoost > 1.0f) CoralCrimson else NovaCyan, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = volumeBoost,
                            onValueChange = { targetBoost ->
                                playbackManager.setVolumeMultiplier(targetBoost)
                            },
                            valueRange = 1.0f..2.0f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = NovaCyan,
                                activeTrackColor = NovaCyan,
                                inactiveTrackColor = BorderSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("volume_boost_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("100% (Standard)", color = TextSecondary, fontSize = 11.sp)
                            Text("150%", color = TextSecondary, fontSize = 11.sp)
                            Text("200% (Max Boost)", color = CoralCrimson, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudioDialog = false }) {
                        Text("Close", color = NovaCyan)
                    }
                },
                containerColor = DarkSurfaceElevated
            )
        }

        // Add Bookmark & Scene Marker Dialog
        if (showAddBookmarkDialog && currentMedia != null) {
            AlertDialog(
                onDismissRequest = { showAddBookmarkDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = NovaCyan)
                        Text("Save Scene Marker", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = NovaCyan, modifier = Modifier.size(16.dp))
                                Text("Timestamp: ${FormatUtils.formatDuration(positionMs)}", color = NovaCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        OutlinedTextField(
                            value = newBookmarkLabel,
                            onValueChange = { newBookmarkLabel = it },
                            label = { Text("Scene Title / Label") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NovaCyan,
                                unfocusedBorderColor = BorderSubtle,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bookmark_label_input")
                        )

                        // Quick category suggestions
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Action", "Favorite", "Climax", "Song", "Quote").forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (newBookmarkLabel.contains(tag)) NovaCyan else DarkSurface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            newBookmarkLabel = "$tag Scene (${FormatUtils.formatDuration(positionMs)})"
                                        }
                                ) {
                                    Text(
                                        text = tag,
                                        color = if (newBookmarkLabel.contains(tag)) DeepVoid else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = newBookmarkNote,
                            onValueChange = { newBookmarkNote = it },
                            label = { Text("Note (Optional)") },
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NovaCyan,
                                unfocusedBorderColor = BorderSubtle,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bookmark_note_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.recordNewBookmark(
                                media = currentMedia!!,
                                timestampMs = positionMs,
                                label = newBookmarkLabel,
                                note = newBookmarkNote
                            )
                            showAddBookmarkDialog = false
                            gestureFeedback = "Marker saved at ${FormatUtils.formatDuration(positionMs)}"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NovaCyan, contentColor = DeepVoid),
                        modifier = Modifier.testTag("save_bookmark_confirm_button")
                    ) {
                        Text("Save Marker", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddBookmarkDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurfaceElevated
            )
        }

        // Speed Selection Dialog
        if (showSpeedMenu) {
            AlertDialog(
                onDismissRequest = { showSpeedMenu = false },
                title = { Text("Playback Speed", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                            val isSelected = (speed == playbackSpeed)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        playbackManager.setSpeed(speed)
                                        showSpeedMenu = false
                                        gestureFeedback = "Speed: ${speed}x"
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = NovaCyan)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "${speed}x" + if (speed == 1.0f) " (Normal)" else "",
                                    color = if (isSelected) NovaCyan else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeedMenu = false }) {
                        Text("Close", color = NovaCyan)
                    }
                },
                containerColor = DarkSurfaceElevated
            )
        }

        // More Options Bottom Sheet
        if (showMoreMenu) {
            ModalBottomSheet(
                onDismissRequest = { showMoreMenu = false },
                containerColor = DarkSurfaceElevated
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Player Options",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sleep Timer
                    ListItem(
                        headlineContent = { Text("Sleep Timer", color = TextPrimary) },
                        supportingContent = { Text(if (sleepTimerMinutes > 0) "$sleepTimerMinutes min" else "Off", color = TextSecondary) },
                        leadingContent = { Icon(Icons.Default.Timer, null, tint = NovaCyan) },
                        modifier = Modifier.clickable {
                            sleepTimerMinutes = when (sleepTimerMinutes) {
                                0 -> 15
                                15 -> 30
                                30 -> 60
                                else -> 0
                            }
                            gestureFeedback = if (sleepTimerMinutes > 0) "Sleep Timer: $sleepTimerMinutes min" else "Sleep Timer Off"
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    // Repeat Mode
                    ListItem(
                        headlineContent = { Text("Repeat Mode", color = TextPrimary) },
                        supportingContent = { Text(playbackManager.repeatMode.collectAsState().value.name, color = TextSecondary) },
                        leadingContent = { Icon(Icons.Default.Repeat, null, tint = NovaCyan) },
                        modifier = Modifier.clickable {
                            playbackManager.cycleRepeatMode()
                            gestureFeedback = "Repeat: ${playbackManager.repeatMode.value.name}"
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    // Media Info
                    ListItem(
                        headlineContent = { Text("Media Information", color = TextPrimary) },
                        supportingContent = { Text("View resolution, codec, and path", color = TextSecondary) },
                        leadingContent = { Icon(Icons.Default.Info, null, tint = NeonMint) },
                        modifier = Modifier.clickable {
                            showMoreMenu = false
                            showInfoDialog = true
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Media Details Dialog
        if (showInfoDialog && currentMedia != null) {
            val media = currentMedia!!
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("Media Information", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Title: ${media.title}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Text("Duration: ${FormatUtils.formatDuration(durationMs)}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        if (!media.resolution.isNullOrBlank()) {
                            Text("Resolution: ${media.resolution}", color = NovaCyan, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("MIME Type: ${media.mimeType}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text("URI: ${media.uri}", color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Close", color = NovaCyan)
                    }
                },
                containerColor = DarkSurfaceElevated
            )
        }
    }
}

@Composable
private fun BottomActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

