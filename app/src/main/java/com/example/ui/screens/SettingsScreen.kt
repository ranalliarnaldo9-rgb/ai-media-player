package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DecoderPreference
import com.example.ui.theme.*
import com.example.viewmodel.MediaViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val settingsManager = viewModel.settingsManager
    val scope = rememberCoroutineScope()

    val isAmoled by settingsManager.isAmoled.collectAsState(initial = false)
    val isGestures by settingsManager.isGesturesEnabled.collectAsState(initial = true)
    val isBackgroundAudio by settingsManager.isBackgroundAudioEnabled.collectAsState(initial = true)
    val decoderPref by settingsManager.decoderPreference.collectAsState(initial = DecoderPreference.AUTO)

    var showDecoderMenu by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DeepVoid)
            .padding(horizontal = 16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        // Playback & Video Engine Section
        item {
            SettingsCategoryHeader("Video & Audio Engine")
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DarkSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Decoder Preference
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Decoder Engine", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(decoderPref.label, color = NovaCyan, style = MaterialTheme.typography.bodySmall)
                        }

                        Box {
                            TextButton(onClick = { showDecoderMenu = true }) {
                                Text("Change", color = NovaCyan)
                            }
                            DropdownMenu(
                                expanded = showDecoderMenu,
                                onDismissRequest = { showDecoderMenu = false },
                                modifier = Modifier.background(DarkSurfaceElevated)
                            ) {
                                DecoderPreference.values().forEach { pref ->
                                    DropdownMenuItem(
                                        text = { Text(pref.label, color = if (pref == decoderPref) NovaCyan else TextPrimary) },
                                        onClick = {
                                            scope.launch { settingsManager.setDecoderPreference(pref) }
                                            showDecoderMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = BorderSubtle)

                    // Background Audio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Background Audio", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Continue playback when screen is locked", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = isBackgroundAudio,
                            onCheckedChange = { scope.launch { settingsManager.setBackgroundAudio(it) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = NovaCyan, checkedTrackColor = NovaCyan.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }

        // Gesture Controls Section
        item {
            SettingsCategoryHeader("Gesture Controls")
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DarkSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Player Gestures", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Swipe vertically for volume/brightness, horizontal to seek", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = isGestures,
                            onCheckedChange = { scope.launch { settingsManager.setGesturesEnabled(it) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = NovaCyan, checkedTrackColor = NovaCyan.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }

        // Appearance Section
        item {
            SettingsCategoryHeader("Appearance & Theme")
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DarkSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AMOLED Pure Black", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Use absolute black (#000000) for OLED battery savings", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = isAmoled,
                            onCheckedChange = { scope.launch { settingsManager.setAmoled(it) } },
                            colors = SwitchDefaults.colors(checkedThumbColor = NovaCyan, checkedTrackColor = NovaCyan.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }

        // About NOVA
        item {
            SettingsCategoryHeader("About")
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DarkSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text("NOVA Media Player", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Version 2.0 (Online Stream & Resumable Downloader)", color = NovaCyan, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("All-in-one local and online media engine built with Jetpack Compose and AndroidX Media3.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun SettingsCategoryHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = NovaCyan
    )
}
