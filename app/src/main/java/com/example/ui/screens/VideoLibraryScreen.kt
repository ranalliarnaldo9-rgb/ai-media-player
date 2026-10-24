package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.MediaItemModel
import com.example.model.MediaType
import com.example.model.SortOption
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.FormatUtils
import com.example.viewmodel.MediaViewModel

@Composable
fun VideoLibraryScreen(
    viewModel: MediaViewModel,
    onPlayMedia: (MediaItemModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val deviceVideos by viewModel.allVideos.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.videoSortOption.collectAsState()
    val context = LocalContext.current

    var activeFilter by remember { mutableStateOf("All") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showStreamHistorySheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }

    // Fallback showcase items if device has zero video files
    val sampleVideos = remember {
        listOf(
            MediaItemModel(
                id = "sample_1",
                title = "Interstellar Space Visuals 4K",
                path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                durationMs = 724000L,
                sizeBytes = 680 * 1024 * 1024L,
                resolution = "4K HDR",
                mediaType = MediaType.VIDEO,
                playbackPositionMs = 470600L,
                isFavorite = true
            ),
            MediaItemModel(
                id = "sample_2",
                title = "Neon Cyberpunk City Ambience Loop",
                path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                durationMs = 2730000L,
                sizeBytes = 340 * 1024 * 1024L,
                resolution = "1080p FHD",
                mediaType = MediaType.VIDEO,
                playbackPositionMs = 546000L,
                isFavorite = false
            ),
            MediaItemModel(
                id = "sample_3",
                title = "High Octane Racing Highlights 2026",
                path = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                durationMs = 495000L,
                sizeBytes = 180 * 1024 * 1024L,
                resolution = "4K 60FPS",
                mediaType = MediaType.VIDEO,
                playbackPositionMs = 445500L,
                isFavorite = true
            )
        )
    }

    val videos = if (deviceVideos.isNotEmpty()) deviceVideos else sampleVideos

    val filteredVideos = remember(videos, searchQuery, activeFilter, sortOption) {
        var list = if (searchQuery.isBlank()) videos else {
            videos.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.folderName.contains(searchQuery, ignoreCase = true)
            }
        }

        list = when (activeFilter) {
            "Recently Played" -> list.filter { it.playbackPositionMs > 0 }
            "4K & HD" -> list.filter { (it.resolution ?: "").contains("4K") || (it.resolution ?: "").contains("1080") || (it.resolution ?: "").contains("HD") }
            "Favorites" -> list.filter { it.isFavorite }
            "Downloaded" -> list.filter { it.uri.startsWith("content://") || it.uri.startsWith("file://") }
            else -> list
        }

        when (sortOption) {
            SortOption.DATE_DESC -> list.sortedByDescending { it.dateAdded }
            SortOption.DATE_ASC -> list.sortedBy { it.dateAdded }
            SortOption.NAME_ASC -> list.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> list.sortedByDescending { it.title.lowercase() }
            SortOption.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
            SortOption.SIZE_ASC -> list.sortedBy { it.sizeBytes }
            SortOption.DURATION_DESC -> list.sortedByDescending { it.durationMs }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepVoid)
            .padding(horizontal = 16.dp)
            .testTag("video_library_screen")
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search videos, streams...", color = TextSecondary, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = NovaCyan, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, "Clear", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("video_search_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NovaCyan,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                        .testTag("sort_videos_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Sort, "Sort", tint = NovaCyan)
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(DarkSurfaceElevated)
                ) {
                    SortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label, color = if (option == sortOption) NovaCyan else TextPrimary) },
                            onClick = {
                                viewModel.setVideoSort(option)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val chips = listOf("All", "Recently Played", "4K & HD", "Favorites", "Downloaded")
            items(chips) { chip ->
                val isSelected = (activeFilter == chip)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) NovaCyan else DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) NovaCyan else BorderSubtle),
                    modifier = Modifier
                        .clickable { activeFilter = chip }
                        .testTag("filter_chip_$chip")
                ) {
                    Text(
                        text = chip,
                        color = if (isSelected) DeepVoid else TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Tools Horizontal Row
        Text(
            text = "QUICK TOOLS",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                QuickToolPill(
                    icon = Icons.Default.Bookmark,
                    label = "Bookmarks & Markers",
                    onClick = { showBookmarksSheet = true }
                )
            }
            item {
                QuickToolPill(
                    icon = Icons.Default.History,
                    label = "Stream History",
                    onClick = { showStreamHistorySheet = true }
                )
            }
            item {
                QuickToolPill(
                    icon = Icons.Default.Subtitles,
                    label = "Subtitle Finder",
                    onClick = { showSubtitleSheet = true }
                )
            }
            item {
                QuickToolPill(
                    icon = Icons.Default.Tune,
                    label = "DSP Equalizer",
                    onClick = { showEqualizerSheet = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Media Feed Header
        Text(
            text = "MEDIA FEED (${filteredVideos.size})",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No videos match your filter", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(filteredVideos, key = { it.id }) { video ->
                    VideoCardItem(
                        media = video,
                        onPlay = { onPlayMedia(video) },
                        onToggleFavorite = { viewModel.toggleFavorite(video) },
                        onShare = {
                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/*"
                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(video.uri))
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                val chooser = Intent.createChooser(shareIntent, "Share Video").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(chooser)
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
        }

        if (showStreamHistorySheet) {
            StreamHistorySheet(
                onDismiss = { showStreamHistorySheet = false },
                onPlayUrl = { url -> viewModel.playStreamDirect(url) }
            )
        }

        if (showSubtitleSheet) {
            SubtitleFinderSheet(
                onDismiss = { showSubtitleSheet = false }
            )
        }

        if (showEqualizerSheet) {
            DspEqualizerSheet(
                onDismiss = { showEqualizerSheet = false }
            )
        }

        if (showBookmarksSheet) {
            BookmarksSheet(
                viewModel = viewModel,
                onDismiss = { showBookmarksSheet = false },
                onPlayBookmark = { bookmark ->
                    val targetVideo = videos.find { it.id == bookmark.mediaId }
                        ?: MediaItemModel(
                            id = bookmark.mediaId,
                            title = bookmark.mediaTitle,
                            path = bookmark.mediaUri,
                            uri = bookmark.mediaUri,
                            durationMs = 0L,
                            sizeBytes = 0L,
                            mimeType = "video/mp4",
                            mediaType = MediaType.VIDEO
                        )
                    viewModel.playMediaFromBookmark(targetVideo, bookmark.timestampMs)
                    onPlayMedia(targetVideo)
                }
            )
        }
    }
}

@Composable
fun QuickToolPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, NovaCyan.copy(alpha = 0.25f)),
        modifier = Modifier
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = NovaCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun VideoCardItem(
    media: MediaItemModel,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit
) {
    val sizeMb = media.sizeBytes / (1024 * 1024)
    val progressPercent = if (media.durationMs > 0 && media.playbackPositionMs > 0) {
        ((media.playbackPositionMs.toFloat() / media.durationMs) * 100).toInt().coerceIn(1, 100)
    } else 0

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .testTag("video_row_${media.id}")
    ) {
        Column {
            // Video Thumbnail / Poster Box with 16:9 Aspect Ratio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(DarkSurfaceElevated)
            ) {
                // Video thumbnail preview if available
                AsyncImage(
                    model = media.uri,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )

                // Quality Badge on Top Left
                val qual = media.resolution ?: "HD"
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = NovaCyan.copy(alpha = 0.25f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NovaCyan.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = qual,
                        color = NovaCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Duration Badge on Bottom Right
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = FormatUtils.formatDuration(media.durationMs),
                        color = NovaCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Playback Resume Progress Bar along bottom of thumbnail
                if (progressPercent > 0) {
                    LinearProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = NovaCyan,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }

            // Info and Controls Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = media.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (progressPercent > 0) {
                            "Resumed at $progressPercent% • $sizeMb MB"
                        } else {
                            "$sizeMb MB • ${media.mimeType.substringAfter("/")}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                // Favorite Button
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (media.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (media.isFavorite) NovaCyan else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Share Button
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VideoItemRow(
    media: MediaItemModel,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit
) {
    VideoCardItem(
        media = media,
        onPlay = onPlay,
        onToggleFavorite = onToggleFavorite,
        onShare = onShare
    )
}
