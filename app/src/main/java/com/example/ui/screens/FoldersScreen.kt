package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.FolderGroup
import com.example.model.MediaItemModel
import com.example.ui.theme.*
import com.example.viewmodel.MediaViewModel

@Composable
fun FoldersScreen(
    viewModel: MediaViewModel,
    onPlayMedia: (MediaItemModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val videos by viewModel.allVideos.collectAsState()
    var selectedFolder by remember { mutableStateOf<String?>(null) }

    val folders = remember(videos) {
        videos.groupBy { it.folderPath }.map { (path, items) ->
            FolderGroup(
                folderPath = path,
                folderName = items.firstOrNull()?.folderName ?: "Videos",
                mediaCount = items.size,
                totalSizeBytes = items.sumOf { it.sizeBytes },
                latestTimestamp = items.maxOfOrNull { it.dateAdded } ?: 0L
            )
        }.sortedByDescending { it.mediaCount }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepVoid)
            .padding(horizontal = 16.dp)
            .testTag("folders_screen")
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        if (selectedFolder != null) {
            val currentFolderVideos = videos.filter { it.folderPath == selectedFolder }
            val folderName = currentFolderVideos.firstOrNull()?.folderName ?: "Folder"

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedFolder = null }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = NovaCyan)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(currentFolderVideos, key = { it.id }) { video ->
                    VideoCardItem(
                        media = video,
                        onPlay = { onPlayMedia(video) },
                        onToggleFavorite = { viewModel.toggleFavorite(video) },
                        onShare = {}
                    )
                }
            }
        } else {
            Text(
                text = "Media Folders (${folders.size})",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (folders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No folders found", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(folders, key = { it.folderPath }) { folder ->
                        val sizeMb = folder.totalSizeBytes / (1024 * 1024)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                .clickable { selectedFolder = folder.folderPath }
                                .testTag("folder_card_${folder.folderName}")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = DarkSurfaceElevated,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Folder, null, tint = NovaCyan, modifier = Modifier.size(26.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder.folderName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${folder.mediaCount} videos • $sizeMb MB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }

                                Icon(Icons.Default.FolderOpen, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
