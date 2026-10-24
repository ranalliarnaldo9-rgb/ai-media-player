package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MediaItemModel
import com.example.model.MediaType
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.FormatUtils
import com.example.viewmodel.MediaViewModel

@Composable
fun MusicLibraryScreen(
    viewModel: MediaViewModel,
    onPlayMedia: (MediaItemModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val deviceAudio by viewModel.allAudio.collectAsState()
    val playbackManager = viewModel.playbackManager
    val currentMedia by playbackManager.currentMedia.collectAsState()
    val isPlaying by playbackManager.isPlaying.collectAsState()
    val positionMs by playbackManager.currentPositionMs.collectAsState()
    val durationMs by playbackManager.durationMs.collectAsState()
    val context = LocalContext.current

    var isShuffleOn by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    // Fallback sample audio track if none on device
    val sampleTrack = remember {
        MediaItemModel(
            id = "sample_audio_1",
            title = "Deep Bass Frequency Test",
            artist = "Acoustic Labs",
            path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            durationMs = 190000L,
            mediaType = MediaType.AUDIO
        )
    }

    val activeAudio = currentMedia ?: deviceAudio.firstOrNull() ?: sampleTrack

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepVoid)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("music_library_screen"),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))

            // Header with Animated Visualizer Bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { Toast.makeText(context, "Audio Library", Toast.LENGTH_SHORT).show() },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(DarkSurfaceElevated)
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = TextSecondary)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AnimatedEqualizerBars(isPlaying = isPlaying)
                    Text(
                        text = "NOVA AUDIO",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                IconButton(
                    onClick = { Toast.makeText(context, "Audio Settings", Toast.LENGTH_SHORT).show() },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(DarkSurfaceElevated)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Glowing Orb Album Cover Card
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = DarkSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaCyan.copy(alpha = 0.4f)),
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(28.dp))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Gradient radial circle
                    Surface(
                        shape = CircleShape,
                        color = NovaCyan.copy(alpha = 0.15f),
                        modifier = Modifier.size(160.dp)
                    ) {}

                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Album Art",
                        tint = NovaCyan,
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Track Info
            Text(
                text = activeAudio.title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = activeAudio.artist ?: "Acoustic Labs",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // LOSSLESS AUDIO Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = NovaCyan.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, NovaCyan.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Headphones, contentDescription = null, tint = NovaCyan, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LOSSLESS AUDIO",
                        color = NovaCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Scrubber
            val activePos = if (currentMedia != null) positionMs else 84000L
            val activeDur = if (currentMedia != null && durationMs > 0) durationMs else activeAudio.durationMs.coerceAtLeast(190000L)
            val clampedPos = activePos.coerceIn(0L, activeDur).toFloat()

            Slider(
                value = clampedPos,
                onValueChange = { targetPos ->
                    playbackManager.seekTo(targetPos.toLong())
                },
                valueRange = 0f..activeDur.toFloat(),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = NovaCyan,
                    activeTrackColor = NovaCyan,
                    inactiveTrackColor = DarkSurfaceVariant
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(FormatUtils.formatDuration(activePos), color = TextSecondary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text(FormatUtils.formatDuration(activeDur), color = TextSecondary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Controls (Shuffle, SkipBack, Play/Pause, SkipForward, Repeat)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { isShuffleOn = !isShuffleOn }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleOn) NovaCyan else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = { playbackManager.seekBackward(10000) }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Big Cyan Play / Pause button
                Surface(
                    shape = CircleShape,
                    color = NovaCyan,
                    modifier = Modifier
                        .size(60.dp)
                        .clickable {
                            if (currentMedia == null) {
                                onPlayMedia(activeAudio)
                            } else {
                                playbackManager.togglePlayPause()
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = DeepVoid,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                IconButton(onClick = { playbackManager.seekForward(10000) }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = { playbackManager.cycleRepeatMode() }) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (playbackManager.repeatMode.collectAsState().value.name != "OFF") NovaCyan else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Audio Actions Grid (QUEUE, EQUALIZER, SLEEP TIMER)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AudioQuickActionItem(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = "QUEUE",
                    onClick = { showQueueSheet = true }
                )
                AudioQuickActionItem(
                    icon = Icons.Default.Tune,
                    label = "EQUALIZER",
                    onClick = { showEqualizerSheet = true }
                )
                AudioQuickActionItem(
                    icon = Icons.Default.Bedtime,
                    label = "SLEEP TIMER",
                    onClick = { showSleepTimerSheet = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tracks Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DEVICE TRACKS (${deviceAudio.size})",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // List of Device Audio Items
        items(deviceAudio, key = { it.id }) { audio ->
            AudioTrackCard(
                audio = audio,
                isSelected = (audio.id == activeAudio.id),
                onPlay = { onPlayMedia(audio) },
                onToggleFavorite = { viewModel.toggleFavorite(audio) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showQueueSheet) {
        AudioQueueSheet(
            tracks = if (deviceAudio.isNotEmpty()) deviceAudio else listOf(sampleTrack),
            currentMedia = currentMedia,
            onPlayTrack = onPlayMedia,
            onDismiss = { showQueueSheet = false }
        )
    }

    if (showEqualizerSheet) {
        DspEqualizerSheet(
            onDismiss = { showEqualizerSheet = false }
        )
    }

    if (showSleepTimerSheet) {
        SleepTimerSheet(
            onDismiss = { showSleepTimerSheet = false },
            onSetTimerMinutes = { min ->
                if (min > 0) {
                    Toast.makeText(context, "Sleep timer set for $min minutes", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Sleep timer turned off", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    }
}

@Composable
fun AnimatedEqualizerBars(isPlaying: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(16.dp)
    ) {
        val transition = rememberInfiniteTransition()
        val h1 by transition.animateFloat(
            initialValue = 4f,
            targetValue = if (isPlaying) 14f else 4f,
            animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse)
        )
        val h2 by transition.animateFloat(
            initialValue = 14f,
            targetValue = if (isPlaying) 6f else 6f,
            animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse)
        )
        val h3 by transition.animateFloat(
            initialValue = 8f,
            targetValue = if (isPlaying) 16f else 8f,
            animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse)
        )

        Box(modifier = Modifier.width(3.dp).height(h1.dp).clip(CircleShape).background(NovaCyan))
        Box(modifier = Modifier.width(3.dp).height(h2.dp).clip(CircleShape).background(NovaCyan))
        Box(modifier = Modifier.width(3.dp).height(h3.dp).clip(CircleShape).background(NovaCyan))
    }
}

@Composable
fun AudioQuickActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = NovaCyan, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
fun AudioTrackCard(
    audio: MediaItemModel,
    isSelected: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) DarkSurfaceElevated else DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) NovaCyan else BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .testTag("audio_row_${audio.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) NovaCyan.copy(alpha = 0.2f) else DarkSurfaceVariant,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Equalizer else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = if (isSelected) NovaCyan else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audio.title,
                    color = if (isSelected) NovaCyan else TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${audio.artist ?: "Unknown Artist"} • ${FormatUtils.formatDuration(audio.durationMs)}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (audio.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (audio.isFavorite) CoralCrimson else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

