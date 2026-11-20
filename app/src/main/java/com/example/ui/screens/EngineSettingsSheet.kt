package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.engine.MaiaDownloadState
import com.example.chess.engine.MaiaModelSpec
import com.example.data.preferences.AnalystSettings
import com.example.ui.theme.ClassBlunder
import com.example.ui.theme.CreamText
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.MutedStoneText
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.SubtleStoneText
import com.example.ui.theme.TournamentEmerald
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineSettingsSheet(
    settings: AnalystSettings,
    activeEngineLabel: String,
    installedMaiaModels: Set<MaiaModelSpec>,
    downloadState: MaiaDownloadState,
    storageUsedMb: Float,
    onDismiss: () -> Unit,
    onDepthChange: (Int) -> Unit,
    onShowCoordinatesChange: (Boolean) -> Unit,
    onShowBestArrowChange: (Boolean) -> Unit,
    onShowMaiaArrowChange: (Boolean) -> Unit,
    onSelectMaiaModel: (MaiaModelSpec) -> Unit,
    onDownloadMaiaModel: (MaiaModelSpec) -> Unit,
    onCancelMaiaDownload: () -> Unit,
    onDeleteMaiaModel: (MaiaModelSpec) -> Unit,
    onMaiaEloChange: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        contentColor = CreamText
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Engine & Model Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = CreamText
            )

            // 1. Tactical Engine Section
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianBg,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = TournamentEmerald)
                        Column {
                            Text("Tactical Engine", style = MaterialTheme.typography.titleSmall, color = TournamentEmerald)
                            Text(activeEngineLabel, style = MaterialTheme.typography.bodySmall, color = MutedStoneText)
                        }
                    }

                    Text("Analysis Depth Preset", style = MaterialTheme.typography.labelMedium, color = CreamText)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(3 to "Fast (D3)", 4 to "Balanced (D4)", 5 to "Deep (D5)")
                        presets.forEach { (d, label) ->
                            FilterChip(
                                selected = settings.searchDepth == d,
                                onClick = { onDepthChange(d) },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TournamentEmerald,
                                    selectedLabelColor = ObsidianBg
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 2. Maia-3 Optional Local Model Manager
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianBg,
                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = GoldAccent)
                            Column {
                                Text("Maia-3 Human Engine (ONNX)", style = MaterialTheme.typography.titleSmall, color = GoldAccent)
                                Text(
                                    text = "Optional local download • Storage used: ${"%.1f".format(storageUsedMb)} MB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedStoneText
                                )
                            }
                        }
                    }

                    Text(
                        text = "Maia-3 predicts realistic human moves and probabilities locally via ONNX Runtime. It complements—never replaces—tactical evaluation. Maia-3 79M is strictly opt-in and never loaded by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SubtleStoneText
                    )

                    // Target Human Elo Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Target Human Skill Level", style = MaterialTheme.typography.labelMedium, color = CreamText)
                            Text(
                                text = "${settings.maiaTargetElo} Elo",
                                fontFamily = JetBrainsMonoFont,
                                fontWeight = FontWeight.Bold,
                                color = GoldAccent
                            )
                        }
                        Slider(
                            value = settings.maiaTargetElo.toFloat(),
                            onValueChange = { onMaiaEloChange(((it / 100f).roundToInt() * 100).coerceIn(600, 2600)) },
                            valueRange = 600f..2600f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = GoldAccent,
                                activeTrackColor = GoldAccent,
                                inactiveTrackColor = ObsidianSurfaceHigh
                            )
                        )
                    }

                    // Download Error
                    if (downloadState is MaiaDownloadState.Error) {
                        Text(
                            text = downloadState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = ClassBlunder
                        )
                    }

                    // Model Size Rows (3M, 5M, 23M, 79M)
                    MaiaModelSpec.entries.forEach { spec ->
                        val installed = spec in installedMaiaModels
                        val isSelected = settings.selectedMaiaModel == spec
                        val isDownloadingThis = downloadState is MaiaDownloadState.Downloading && downloadState.model == spec

                        Surface(
                            onClick = { if (installed) onSelectMaiaModel(spec) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (installed && isSelected) GoldAccent.copy(alpha = 0.14f) else ObsidianSurface,
                            border = BorderStroke(
                                1.dp,
                                if (installed && isSelected) GoldAccent else ObsidianBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = spec.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = CreamText
                                        )
                                        Text(
                                            text = "${spec.sizeMb} MB storage • ${spec.ramRequirementLabel}",
                                            fontFamily = JetBrainsMonoFont,
                                            fontSize = 11.sp,
                                            color = if (spec.isHeavy) ClassBlunder else MutedStoneText
                                        )
                                        Text(
                                            text = spec.recommendation,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SubtleStoneText
                                        )
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    when {
                                        isDownloadingThis -> {
                                            OutlinedButton(
                                                onClick = onCancelMaiaDownload,
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("Cancel", fontSize = 12.sp, color = ClassBlunder)
                                            }
                                        }
                                        installed -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = "Selected model",
                                                        tint = GoldAccent,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                IconButton(onClick = { onDeleteMaiaModel(spec) }) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = "Delete ${spec.displayName}",
                                                        tint = SubtleStoneText
                                                    )
                                                }
                                            }
                                        }
                                        else -> {
                                            Button(
                                                onClick = { onDownloadMaiaModel(spec) },
                                                enabled = downloadState !is MaiaDownloadState.Downloading,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (spec.isHeavy) ObsidianSurfaceHigh else GoldAccent,
                                                    contentColor = if (spec.isHeavy) CreamText else ObsidianBg
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("${spec.sizeMb} MB", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                if (isDownloadingThis && downloadState is MaiaDownloadState.Downloading) {
                                    LinearProgressIndicator(
                                        progress = { downloadState.progress },
                                        color = GoldAccent,
                                        trackColor = ObsidianSurfaceHigh,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    Text(
                                        text = "${"%.1f".format(downloadState.downloadedMb)} / ${"%.1f".format(downloadState.totalMb)} MB",
                                        fontFamily = JetBrainsMonoFont,
                                        fontSize = 10.5.sp,
                                        color = GoldAccent
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Board Display Settings
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianBg,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SettingsSwitchRow("Board Coordinates (a–h, 1–8)", settings.showCoordinates, onShowCoordinatesChange)
                    HorizontalDivider(color = ObsidianBorder)
                    SettingsSwitchRow("Best Move Arrow (Green)", settings.showBestMoveArrow, onShowBestArrowChange)
                    HorizontalDivider(color = ObsidianBorder)
                    SettingsSwitchRow("Maia-3 Human Move Arrow (Gold)", settings.showMaiaArrow, onShowMaiaArrowChange)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CreamText)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = TournamentEmerald)
        )
    }
}
