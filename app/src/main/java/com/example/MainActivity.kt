package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.model.MediaItemModel
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.NetworkStreamDialog
import com.example.ui.components.ResolutionSelectionSheet
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.viewmodel.MediaViewModel

enum class ScreenTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    VIDEOS("Videos", Icons.Default.VideoLibrary),
    FOLDERS("Folders", Icons.Default.Folder),
    AUDIO("Audio", Icons.Default.MusicNote),
    DOWNLOADS("Downloads", Icons.Default.Download),
    VAULT("Vault", Icons.Default.Lock)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MediaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NovaPlayerTheme {
                NovaApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaApp(viewModel: MediaViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(ScreenTab.VIDEOS) }
    var isVideoPlayerActive by remember { mutableStateOf(false) }
    var isSettingsActive by remember { mutableStateOf(false) }

    val allDownloads by viewModel.allDownloads.collectAsState()
    val activeDownloadCount = allDownloads.count { it.status in listOf("DOWNLOADING", "QUEUED", "PAUSED") }

    val showStreamDialog by viewModel.showStreamDialog.collectAsState()
    val isInspectingStream by viewModel.isInspectingStream.collectAsState()
    val inspectionResult by viewModel.inspectionResult.collectAsState()
    val showResolutionSheet by viewModel.showResolutionSheet.collectAsState()

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            viewModel.rescanMedia()
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepVoid)) {
        if (isVideoPlayerActive) {
            VideoPlayerScreen(
                viewModel = viewModel,
                onNavigateBack = { isVideoPlayerActive = false }
            )
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = NovaCyan,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.PlayArrow, null, tint = DeepVoid, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isSettingsActive) "Settings" else "NOVA",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                if (!isSettingsActive) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = NovaCyan.copy(alpha = 0.15f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NovaCyan.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = "PRO",
                                            color = NovaCyan,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = DeepVoid
                        ),
                        actions = {
                            if (!isSettingsActive) {
                                IconButton(
                                    onClick = {
                                        android.widget.Toast.makeText(context, "Searching for Cast devices...", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("top_cast_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cast,
                                        contentDescription = "Cast",
                                        tint = TextSecondary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        selectedTab = ScreenTab.DOWNLOADS
                                    },
                                    modifier = Modifier.testTag("top_bell_button")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (activeDownloadCount > 0) {
                                                Badge(containerColor = NovaCyan) {}
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = "Notifications",
                                            tint = TextSecondary
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { isSettingsActive = true },
                                    modifier = Modifier.testTag("top_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = TextSecondary
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { isSettingsActive = false },
                                    modifier = Modifier.testTag("close_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Settings",
                                        tint = TextPrimary
                                    )
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    Column {
                        // Mini player floating bar
                        MiniPlayerBar(
                            playbackManager = viewModel.playbackManager,
                            onExpandPlayer = {
                                isVideoPlayerActive = true
                            }
                        )

                        // Bottom Navigation Bar with 5 tabs
                        NavigationBar(
                            containerColor = DarkSurfaceElevated,
                            tonalElevation = 8.dp,
                            modifier = Modifier.testTag("main_bottom_nav")
                        ) {
                            ScreenTab.values().forEach { tab ->
                                val isSelected = (selectedTab == tab && !isSettingsActive)

                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        isSettingsActive = false
                                        selectedTab = tab
                                    },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (tab == ScreenTab.DOWNLOADS && activeDownloadCount > 0) {
                                                    Badge(containerColor = NovaCyan, contentColor = DeepVoid) {
                                                        Text("$activeDownloadCount")
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = tab.title,
                                                tint = if (isSelected) NovaCyan else TextSecondary
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            color = if (isSelected) NovaCyan else TextSecondary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = NovaCyan,
                                        selectedTextColor = NovaCyan,
                                        indicatorColor = NovaCyan.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isSettingsActive) {
                        SettingsScreen(viewModel = viewModel)
                    } else {
                        when (selectedTab) {
                            ScreenTab.VIDEOS -> VideoLibraryScreen(
                                viewModel = viewModel,
                                onPlayMedia = { media ->
                                    viewModel.startPlayback(media)
                                    isVideoPlayerActive = true
                                }
                            )
                            ScreenTab.FOLDERS -> FoldersScreen(
                                viewModel = viewModel,
                                onPlayMedia = { media ->
                                    viewModel.startPlayback(media)
                                    isVideoPlayerActive = true
                                }
                            )
                            ScreenTab.AUDIO -> MusicLibraryScreen(
                                viewModel = viewModel,
                                onPlayMedia = { media ->
                                    viewModel.startPlayback(media)
                                }
                            )
                            ScreenTab.DOWNLOADS -> DownloadsScreen(
                                viewModel = viewModel,
                                onPlayMedia = { media ->
                                    viewModel.startPlayback(media)
                                    isVideoPlayerActive = true
                                }
                            )
                            ScreenTab.VAULT -> PrivateVaultScreen(
                                viewModel = viewModel,
                                onPlayMedia = { media ->
                                    viewModel.startPlayback(media)
                                    isVideoPlayerActive = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // Network Stream Dialog
        NetworkStreamDialog(
            isOpen = showStreamDialog,
            onDismiss = { viewModel.closeStreamDialog() },
            onPlay = { url ->
                viewModel.playStreamDirect(url)
                isVideoPlayerActive = true
            },
            onInspectDownload = { url ->
                viewModel.inspectAndPrepareDownload(url)
            },
            onDirectDownload = { url ->
                viewModel.startDirectDownload(url)
            },
            isInspecting = isInspectingStream,
            inspectionResult = inspectionResult
        )

        // Resolution Bottom Sheet
        ResolutionSelectionSheet(
            isOpen = showResolutionSheet,
            onDismiss = { viewModel.closeResolutionSheet() },
            result = inspectionResult,
            onConfirmDownload = { variant ->
                viewModel.startDownload(variant)
            }
        )
    }
}
