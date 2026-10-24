package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MediaItemModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DspEqualizerSheet(
    onDismiss: () -> Unit
) {
    var selectedPreset by remember { mutableStateOf("Bass Boost") }
    var bassBoostEnabled by remember { mutableStateOf(true) }
    var band60 by remember { mutableFloatStateOf(6f) }
    var band230 by remember { mutableFloatStateOf(4f) }
    var band910 by remember { mutableFloatStateOf(0f) }
    var band4k by remember { mutableFloatStateOf(3f) }
    var band14k by remember { mutableFloatStateOf(5f) }

    val presets = listOf("Bass Boost", "Cyberpunk", "Rock", "EDM", "Vocal", "Flat")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderSubtle) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = NovaCyan)
                    Text("DSP Hardware Equalizer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = NovaCyan.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NovaCyan.copy(alpha = 0.4f))
                ) {
                    Text("32-BIT HI-RES", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = NovaCyan, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Presets
            Text("SOUND PRESETS", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets) { preset ->
                    val isSelected = selectedPreset == preset
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) NovaCyan else DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) NovaCyan else BorderSubtle),
                        modifier = Modifier.clickable {
                            selectedPreset = preset
                            when (preset) {
                                "Bass Boost" -> { band60 = 8f; band230 = 5f; band910 = 0f; band4k = 2f; band14k = 4f; bassBoostEnabled = true }
                                "Cyberpunk" -> { band60 = 9f; band230 = 3f; band910 = -2f; band4k = 6f; band14k = 8f; bassBoostEnabled = true }
                                "Rock" -> { band60 = 5f; band230 = 3f; band910 = -1f; band4k = 4f; band14k = 6f }
                                "EDM" -> { band60 = 7f; band230 = 4f; band910 = 1f; band4k = 5f; band14k = 7f }
                                "Vocal" -> { band60 = -2f; band230 = 1f; band910 = 6f; band4k = 4f; band14k = 1f }
                                "Flat" -> { band60 = 0f; band230 = 0f; band910 = 0f; band4k = 0f; band14k = 0f; bassBoostEnabled = false }
                            }
                        }
                    ) {
                        Text(
                            text = preset,
                            color = if (isSelected) DeepVoid else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5 Band Sliders
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EqualizerBandColumn(label = "60 Hz", value = band60, onValueChange = { band60 = it })
                EqualizerBandColumn(label = "230 Hz", value = band230, onValueChange = { band230 = it })
                EqualizerBandColumn(label = "910 Hz", value = band910, onValueChange = { band910 = it })
                EqualizerBandColumn(label = "4 kHz", value = band4k, onValueChange = { band4k = it })
                EqualizerBandColumn(label = "14 kHz", value = band14k, onValueChange = { band14k = it })
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bass Boost & Surround Toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = NovaCyan)
                    Column {
                        Text("Ultra Bass Dynamic Boost", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Sub-harmonic acoustic resonance", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
                Switch(
                    checked = bassBoostEnabled,
                    onCheckedChange = { bassBoostEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DeepVoid,
                        checkedTrackColor = NovaCyan,
                        uncheckedTrackColor = DarkSurfaceElevated
                    )
                )
            }
        }
    }
}

@Composable
private fun EqualizerBandColumn(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${value.toInt()} dB", color = NovaCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -10f..10f,
            modifier = Modifier
                .width(55.dp)
                .height(130.dp),
            colors = SliderDefaults.colors(
                thumbColor = NovaCyan,
                activeTrackColor = NovaCyan,
                inactiveTrackColor = BorderSubtle
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    onDismiss: () -> Unit,
    onSetTimerMinutes: (Int) -> Unit
) {
    val times = listOf(15, 30, 45, 60, 90)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderSubtle) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Bedtime, contentDescription = null, tint = NovaCyan)
                Text("Sleep Timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Audio playback will stop automatically after the selected timer.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

            Spacer(modifier = Modifier.height(16.dp))

            times.forEach { min ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            onSetTimerMinutes(min)
                            onDismiss()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$min Minutes", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    onSetTimerMinutes(0)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Turn Off Sleep Timer", color = CoralCrimson)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamHistorySheet(
    onDismiss: () -> Unit,
    onPlayUrl: (String) -> Unit
) {
    val sampleStreams = listOf(
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" to "Big Buck Bunny 1080p",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4" to "Elephants Dream 4K",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4" to "For Bigger Blazes HDR",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4" to "Tears of Steel Sci-Fi"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderSubtle) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.History, contentDescription = null, tint = NovaCyan)
                Text("Stream History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sampleStreams) { (url, label) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPlayUrl(url)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, color = TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(url, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                            IconButton(onClick = { onPlayUrl(url); onDismiss() }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = NovaCyan)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleFinderSheet(
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderSubtle) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Subtitles, contentDescription = null, tint = NovaCyan)
                Text("Subtitle Finder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Select subtitle tracks for current video or load local .srt/.vtt file.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

            Spacer(modifier = Modifier.height(16.dp))

            listOf("English [CC] (Embedded)", "Spanish (SubRip .srt)", "Urdu / Hindi (External)", "Auto-Sync Timing (+0.5s)").forEach { sub ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onDismiss() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(sub, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Icon(Icons.Default.Check, contentDescription = null, tint = NovaCyan, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioQueueSheet(
    tracks: List<MediaItemModel>,
    currentMedia: MediaItemModel?,
    onPlayTrack: (MediaItemModel) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderSubtle) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = NovaCyan)
                Text("Play Queue (${tracks.size} tracks)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (tracks.isEmpty()) {
                Text("No audio tracks in current queue.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(tracks) { track ->
                        val isCurrent = track.id == currentMedia?.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrent) NovaCyan.copy(alpha = 0.15f) else DarkSurface,
                            border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, NovaCyan) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPlayTrack(track)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, color = if (isCurrent) NovaCyan else TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Text(track.artist ?: "Unknown Artist", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                                if (isCurrent) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = "Playing", tint = NovaCyan)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
