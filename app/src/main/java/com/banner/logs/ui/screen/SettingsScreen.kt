package com.banner.logs.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.banner.logs.viewmodel.LogViewModel
import com.banner.logs.viewmodel.UiState
import kotlin.math.roundToInt

private val MAX_LINE_OPTIONS = listOf(500, 1000, 2000, 5000, 10000, 20000, 0)

private fun maxLineLabel(n: Int) = if (n == 0) "∞" else n.toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    uiState: UiState,
    viewModel: LogViewModel,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(8.dp))

            // --- Display ---
            SectionLabel("Display")

            SettingsRow(label = "Font Size: ${uiState.fontSize.roundToInt()}sp") {
                Slider(
                    value = uiState.fontSize,
                    onValueChange = viewModel::setFontSize,
                    valueRange = 9f..18f,
                    steps = 8,
                    modifier = Modifier.weight(1f)
                )
            }

            SwitchRow("Wrap Long Lines", uiState.wrapLines, viewModel::setWrapLines)
            SwitchRow("Show Timestamp", uiState.showTimestamp, viewModel::setShowTimestamp)
            SwitchRow("Show PID / TID", uiState.showPidTid, viewModel::setShowPidTid)
            SwitchRow("Auto-Scroll to Bottom", uiState.autoScroll, viewModel::setAutoScroll)

            Spacer(Modifier.height(12.dp))

            // --- Buffer ---
            SectionLabel("Log Buffer")
            Text(
                text = "Max lines held in memory  —  ∞ = unlimited (uses more RAM)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 4.dp)
            ) {
                items(MAX_LINE_OPTIONS) { n ->
                    FilterChip(
                        selected = uiState.maxLines == n,
                        onClick = { viewModel.setMaxLines(n) },
                        label = {
                            Text(
                                text = maxLineLabel(n),
                                fontWeight = if (uiState.maxLines == n) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // --- Export Location ---
            val context = LocalContext.current
            val folderPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri != null) viewModel.setExportUri(context, uri)
            }

            SectionLabel("Export Location")
            Text(
                text = "Where the save button writes log files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    if (uiState.exportPathDisplay.isNotEmpty()) {
                        Text(
                            text = uiState.exportPathDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "/sdcard/ (default)",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (uiState.exportUriString.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearExportUri(context) }) {
                            Icon(Icons.Default.Close, contentDescription = "Reset to default")
                        }
                    }
                    OutlinedButton(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Choose")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // --- Live File ---
            SectionLabel("Live File Output")
            Text(
                text = "Streams filtered logs to a file in real time. Keep app open or minimised — foreground service holds it alive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.liveFileEnabled) {
                        Icon(
                            Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (uiState.liveFileEnabled) "Live — recording" else "Stopped",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.liveFileEnabled) Color(0xFFEF5350)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.liveFileEnabled,
                    onCheckedChange = { viewModel.toggleLiveFile(context) }
                )
            }
            if (uiState.liveFileEnabled && uiState.liveFilePath.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "tail -f ${uiState.liveFilePath}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsRow(label: String, content: @Composable RowScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}
