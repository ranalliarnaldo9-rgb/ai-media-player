package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.download.StreamInspectionResult
import com.example.model.StreamItem
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolutionSelectionSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    result: StreamInspectionResult?,
    onConfirmDownload: (StreamItem) -> Unit
) {
    if (!isOpen || result !is StreamInspectionResult.Success) return

    val variants = result.variants
    var selectedVariant by remember(variants) { mutableStateOf(variants.firstOrNull()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = TextSecondary.copy(alpha = 0.5f))
        },
        modifier = Modifier.testTag("resolution_selection_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Select Stream Quality",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = if (result.isHls) "Verified variants from master playlist" else "Single progressive stream quality",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(variants) { variant ->
                    val isSelected = (variant == selectedVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) NovaCyan.copy(alpha = 0.1f) else DarkSurface)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) NovaCyan else BorderSubtle,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedVariant = variant }
                            .padding(14.dp)
                            .testTag("variant_row_${variant.resolution}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Unselected",
                            tint = if (isSelected) NovaCyan else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = variant.resolution.ifEmpty { "Standard Quality" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                if (variant.width > 0 && variant.height > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = DarkSurfaceVariant
                                    ) {
                                        Text(
                                            text = "${variant.width}x${variant.height}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (variant.bitrate > 0) {
                                    val mbps = String.format("%.1f Mbps", variant.bitrate.toDouble() / 1_000_000)
                                    Text("Bitrate: $mbps", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                if (variant.estimatedSizeBytes > 0) {
                                    val sizeMb = variant.estimatedSizeBytes / (1024 * 1024)
                                    Text("~${sizeMb} MB", color = NeonMint, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { selectedVariant?.let { onConfirmDownload(it) } },
                enabled = selectedVariant != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NovaCyan,
                    contentColor = DeepVoid
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("start_download_button")
            ) {
                Icon(Icons.Default.Download, contentDescription = "Start Download")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Background Download", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
