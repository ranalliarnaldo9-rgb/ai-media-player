package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.download.StreamInspectionResult
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CoralCrimson
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DeepVoid
import com.example.ui.theme.NeonMint
import com.example.ui.theme.NovaCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun NetworkStreamDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onPlay: (String) -> Unit,
    onInspectDownload: (String) -> Unit,
    onDirectDownload: ((String) -> Unit)? = null,
    isInspecting: Boolean,
    inspectionResult: StreamInspectionResult?
) {
    if (!isOpen) return

    val clipboardManager = LocalClipboardManager.current
    var urlText by remember { mutableStateOf("") }
    val isHls = urlText.contains(".m3u8", ignoreCase = true)
    val isMp4 = urlText.contains(".mp4", ignoreCase = true) || urlText.contains(".webm", ignoreCase = true)
    val isYouTube = urlText.contains("youtube.com", ignoreCase = true) || urlText.contains("youtu.be", ignoreCase = true)
    val isWebSocial = urlText.contains("instagram.com", ignoreCase = true) || urlText.contains("tiktok.com", ignoreCase = true) || urlText.contains("facebook.com", ignoreCase = true)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, BorderHighlight, RoundedCornerShape(20.dp))
                .testTag("network_stream_dialog"),
            color = DarkSurfaceElevated,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Stream Icon",
                        tint = NovaCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Play or Download Link",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // URL Input Field
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stream_url_input"),
                    placeholder = { Text("Paste direct MP4, WebM, MP3 or HLS (.m3u8) URL", color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NovaCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = NovaCyan
                    ),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (urlText.isNotEmpty()) {
                                IconButton(
                                    onClick = { urlText = "" },
                                    modifier = Modifier.testTag("clear_url_button")
                                ) {
                                    Icon(Icons.Default.Clear, "Clear", tint = TextSecondary)
                                }
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.getText()?.let {
                                        urlText = it.text.trim()
                                    }
                                },
                                modifier = Modifier.testTag("paste_url_button")
                            ) {
                                Icon(Icons.Default.ContentPaste, "Paste", tint = NovaCyan)
                            }
                        }
                    }
                )

                // Stream Format Badges
                if (urlText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        if (isHls) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NovaCyan.copy(alpha = 0.15f),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "HLS Manifest (.m3u8)",
                                    color = NovaCyan,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else if (isMp4) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NeonMint.copy(alpha = 0.15f),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "Direct Media File",
                                    color = NeonMint,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (isYouTube) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NovaCyan.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NovaCyan.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayCircle, null, tint = NovaCyan, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "🌟 YouTube Video: Tap 'Play' to stream directly in Nova Web Player.\n(For offline download, use direct .mp4 or .m3u8 links).",
                                    color = NovaCyan,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else if (isWebSocial) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NeonMint.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonMint.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayCircle, null, tint = NeonMint, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "🌟 Web Stream: Tap 'Play' to stream directly via Universal Web Media Engine.",
                                    color = NeonMint,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // Inspection Error Message with Direct Download Fallback
                if (inspectionResult is StreamInspectionResult.Error) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CoralCrimson.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CoralCrimson.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = inspectionResult.message,
                                color = CoralCrimson,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { onDirectDownload?.invoke(urlText) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NovaCyan,
                                    contentColor = DeepVoid
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Direct Download Anyway", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Sample Streams for One-Tap Testing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Try:", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurfaceVariant,
                        modifier = Modifier.clickable {
                            urlText = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                        }.testTag("sample_mp4_pill")
                    ) {
                        Text(
                            text = "Sample MP4",
                            color = NovaCyan,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurfaceVariant,
                        modifier = Modifier.clickable {
                            urlText = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                        }.testTag("sample_hls_pill")
                    ) {
                        Text(
                            text = "Sample HLS (.m3u8)",
                            color = NeonMint,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_stream_button")
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = {
                            if (onDirectDownload != null && !isHls) {
                                onDirectDownload(urlText)
                            } else {
                                onInspectDownload(urlText)
                            }
                        },
                        enabled = urlText.isNotBlank() && !isInspecting,
                        modifier = Modifier.testTag("download_stream_option_button"),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (urlText.isNotBlank()) NovaCyan else BorderSubtle),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NovaCyan)
                    ) {
                        if (isInspecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = NovaCyan
                            )
                        } else {
                            Icon(Icons.Default.Download, "Download", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download")
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onPlay(urlText) },
                        enabled = urlText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NovaCyan,
                            contentColor = DeepVoid
                        ),
                        modifier = Modifier.testTag("play_stream_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, "Play", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play")
                    }
                }
            }
        }
    }
}
