package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.database.DownloadTaskEntity
import com.example.model.MediaItemModel
import com.example.model.MediaType
import com.example.ui.theme.*
import com.example.util.FormatUtils
import com.example.viewmodel.DownloadFilter
import com.example.viewmodel.MediaViewModel

@Composable
fun DownloadsScreen(
    viewModel: MediaViewModel,
    onPlayMedia: (MediaItemModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val downloads by viewModel.filteredDownloads.collectAsState()
    val allDownloads by viewModel.allDownloads.collectAsState()
    val currentFilter by viewModel.downloadFilter.collectAsState()
    val context = LocalContext.current

    val activeCount = allDownloads.count { it.status in listOf("DOWNLOADING", "QUEUED", "PAUSED") }
    val completedCount = allDownloads.count { it.status == "COMPLETED" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepVoid)
            .padding(horizontal = 16.dp)
            .testTag("downloads_screen")
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = NovaCyan.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NovaCyan.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "MULTI-THREAD",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = NovaCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "$activeCount active • $completedCount completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Button(
                onClick = { viewModel.openStreamDialog() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NovaCyan,
                    contentColor = DeepVoid
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("downloads_add_stream_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ New Link", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filter Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentFilter == DownloadFilter.ALL,
                onClick = { viewModel.setDownloadFilter(DownloadFilter.ALL) },
                label = { Text("All (${allDownloads.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NovaCyan,
                    selectedLabelColor = DeepVoid,
                    containerColor = DarkSurfaceElevated,
                    labelColor = TextSecondary
                ),
                modifier = Modifier.testTag("filter_all_chip")
            )

            FilterChip(
                selected = currentFilter == DownloadFilter.ACTIVE,
                onClick = { viewModel.setDownloadFilter(DownloadFilter.ACTIVE) },
                label = { Text("Active ($activeCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NovaCyan,
                    selectedLabelColor = DeepVoid,
                    containerColor = DarkSurfaceElevated,
                    labelColor = TextSecondary
                ),
                modifier = Modifier.testTag("filter_active_chip")
            )

            FilterChip(
                selected = currentFilter == DownloadFilter.COMPLETED,
                onClick = { viewModel.setDownloadFilter(DownloadFilter.COMPLETED) },
                label = { Text("Completed ($completedCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NovaCyan,
                    selectedLabelColor = DeepVoid,
                    containerColor = DarkSurfaceElevated,
                    labelColor = TextSecondary
                ),
                modifier = Modifier.testTag("filter_completed_chip")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (downloads.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = DarkSurfaceElevated,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DownloadForOffline,
                                contentDescription = null,
                                tint = NovaCyan,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No Downloads Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Paste a direct stream or HLS URL to download videos for offline playback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { viewModel.openStreamDialog() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NovaCyan,
                            contentColor = DeepVoid
                        ),
                        modifier = Modifier.testTag("empty_downloads_add_button")
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download Stream")
                    }
                }
            }
        } else {
            val hasCanceledTasks = downloads.any { it.status == "CANCELED" || it.status == "FAILED" }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (hasCanceledTasks) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ElectricAmber.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricAmber.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("restart_all_canceled_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = ElectricAmber, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Canceled / Incomplete Downloads",
                                        color = ElectricAmber,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Jo cancel howa us ko yahan se mukamal karo",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Button(
                                    onClick = { viewModel.completeAllCanceled() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NovaCyan,
                                        contentColor = DeepVoid
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Mukamal Karo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                items(downloads, key = { it.id }) { task ->
                    val isActiveTask = task.status in listOf("DOWNLOADING", "QUEUED", "PAUSED")
                    val isCanceledOrFailed = task.status in listOf("CANCELED", "FAILED")

                    if (isActiveTask) {
                        ActiveDownloadCard(
                            task = task,
                            onPause = { viewModel.pauseDownload(task.id) },
                            onResume = { viewModel.resumeDownload(task) },
                            onCancel = { viewModel.cancelDownload(task.id) }
                        )
                    } else if (isCanceledOrFailed) {
                        CanceledDownloadCard(
                            task = task,
                            onRetry = { viewModel.retryDownload(task) },
                            onComplete = { viewModel.markDownloadCompleted(task) },
                            onDelete = { viewModel.deleteDownload(task) }
                        )
                    } else {
                        CompletedDownloadCard(
                            task = task,
                            onPlay = {
                                val media = MediaItemModel(
                                    id = task.id,
                                    title = task.title,
                                    path = task.localUri ?: "",
                                    uri = task.localUri ?: "",
                                    sizeBytes = task.totalBytes,
                                    resolution = task.resolution,
                                    mediaType = MediaType.VIDEO
                                )
                                onPlayMedia(media)
                            },
                            onDelete = { viewModel.deleteDownload(task) },
                            onShare = {
                                task.localUri?.let { uriStr ->
                                    try {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/*"
                                            putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr))
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        val chooser = Intent.createChooser(shareIntent, "Share Video").apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(chooser)
                                    } catch (_: Exception) {}
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveDownloadCard(
    task: DownloadTaskEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val percent = if (task.totalBytes > 0) ((task.downloadedBytes * 100) / task.totalBytes).toInt() else 0
    val speedText = FormatUtils.formatSpeed(task.speedBytesPerSec)
    val etaText = if (task.etaSeconds > 0) "${task.etaSeconds}s remaining" else "Calculating..."
    val downloadedMb = task.downloadedBytes / (1024 * 1024)
    val totalMb = task.totalBytes / (1024 * 1024)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkSurfaceElevated,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .testTag("active_download_card_${task.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NovaCyan.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (task.status == "PAUSED") Icons.Default.Pause else Icons.Default.Download,
                            contentDescription = null,
                            tint = NovaCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = DarkSurfaceVariant
                        ) {
                            Text(
                                text = task.resolution,
                                style = MaterialTheme.typography.labelSmall,
                                color = NovaCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = if (task.status == "PAUSED") "Paused" else speedText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (task.status == "PAUSED") ElectricAmber else NeonMint
                        )
                    }
                }

                // Control Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.status == "DOWNLOADING") {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, "Pause", tint = TextPrimary)
                        }
                    } else {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, "Resume", tint = NovaCyan)
                        }
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel", tint = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { (percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = NovaCyan,
                trackColor = DarkSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$downloadedMb MB / ${if (totalMb > 0) "$totalMb MB" else "..."} ($percent%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                if (task.status == "DOWNLOADING") {
                    Text(
                        text = etaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun CompletedDownloadCard(
    task: DownloadTaskEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val sizeMb = task.totalBytes / (1024 * 1024)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .clickable { onPlay() }
            .testTag("completed_download_card_${task.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DarkSurfaceElevated,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = "Play",
                        tint = NovaCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = DarkSurfaceVariant
                    ) {
                        Text(
                            text = task.resolution,
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonMint,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "$sizeMb MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Options", tint = TextSecondary)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(DarkSurfaceElevated)
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Video", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = NovaCyan) },
                        onClick = {
                            showMenu = false
                            onPlay()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Share, null, tint = TextSecondary) },
                        onClick = {
                            showMenu = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = CoralCrimson) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = CoralCrimson) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CanceledDownloadCard(
    task: DownloadTaskEntity,
    onRetry: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    val isCanceled = task.status == "CANCELED"
    val statusColor = if (isCanceled) ElectricAmber else CoralCrimson
    val statusLabel = if (isCanceled) "Canceled" else "Failed"

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .testTag("canceled_download_card_${task.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isCanceled) Icons.Default.Cancel else Icons.Default.ErrorOutline,
                            contentDescription = statusLabel,
                            tint = statusColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = statusColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = statusLabel,
                                color = statusColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (!task.resolution.isNullOrBlank()) {
                            Text(
                                text = task.resolution,
                                style = MaterialTheme.typography.labelSmall,
                                color = NovaCyan
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Delete, Restart, and Mukamal Karo (Complete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_canceled_button_${task.id}")
                ) {
                    Icon(Icons.Default.Delete, "Delete", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(6.dp))

                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.testTag("retry_download_button_${task.id}")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(14.dp), tint = TextSecondary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restart", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                }

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NovaCyan,
                        contentColor = DeepVoid
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("complete_download_button_${task.id}")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Complete", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mukamal Karo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

