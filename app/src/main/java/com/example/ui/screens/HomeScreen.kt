package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.chess.analysis.GameReviewReport
import com.example.chess.engine.MaiaModelSpec
import com.example.ui.theme.CreamText
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.MutedStoneText
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.SubtleStoneText
import com.example.ui.theme.TournamentEmerald
import com.example.ui.theme.TournamentEmeraldContainer

@Composable
fun HomeScreen(
    recentReviews: List<GameReviewReport>,
    installedMaiaModels: Set<MaiaModelSpec>,
    selectedMaiaModel: MaiaModelSpec,
    searchDepth: Int,
    onImportPgnClick: () -> Unit,
    onImportFenClick: () -> Unit,
    onChessComClick: () -> Unit,
    onOpenRecentReview: (GameReviewReport) -> Unit,
    onOpenEngineSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Brand Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            color = CreamText
                        )
                        Text(
                            text = stringResource(R.string.home_no_account_badge),
                            style = MaterialTheme.typography.bodySmall,
                            color = TournamentEmerald,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Surface(
                        onClick = onOpenEngineSettings,
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurfaceHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.testTag("home_engine_settings_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = stringResource(R.string.cd_engine_settings),
                                tint = TournamentEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Engines",
                                style = MaterialTheme.typography.labelMedium,
                                color = CreamText
                            )
                        }
                    }
                }
            }

            // Editorial Hero Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_chess_hero_banner_1795153582345),
                        contentDescription = "Chess Analyst board illustration",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x33161512),
                                        Color(0xAA161512),
                                        Color(0xEE161512)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(
                                icon = Icons.Default.Memory,
                                text = "Tactical Depth $searchDepth",
                                containerColor = TournamentEmeraldContainer,
                                contentColor = TournamentEmerald
                            )
                            val maiaReady = selectedMaiaModel in installedMaiaModels
                            StatusPill(
                                icon = Icons.Default.Psychology,
                                text = if (maiaReady) selectedMaiaModel.displayName.substringBefore(" (") else "Maia-3 Optional",
                                containerColor = if (maiaReady) GoldContainer else ObsidianSurfaceHigh,
                                contentColor = if (maiaReady) GoldAccent else MutedStoneText
                            )
                        }
                        Text(
                            text = "Grandmaster-grade Game Review, completely local.",
                            style = MaterialTheme.typography.titleMedium,
                            color = CreamText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Three Prominent Main Actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryActionCard(
                        title = stringResource(R.string.action_import_pgn),
                        subtitle = stringResource(R.string.action_import_pgn_desc),
                        icon = Icons.Default.Description,
                        accentColor = TournamentEmerald,
                        badge = "SAF • Multi-Game",
                        testTag = "home_import_pgn_card",
                        onClick = onImportPgnClick
                    )
                    PrimaryActionCard(
                        title = stringResource(R.string.action_import_fen),
                        subtitle = stringResource(R.string.action_import_fen_desc),
                        icon = Icons.Default.GridOn,
                        accentColor = GoldAccent,
                        badge = "Board Editor • Live Eval",
                        testTag = "home_import_fen_card",
                        onClick = onImportFenClick
                    )
                    PrimaryActionCard(
                        title = stringResource(R.string.action_chess_com),
                        subtitle = stringResource(R.string.action_chess_com_desc),
                        icon = Icons.Default.CloudDownload,
                        accentColor = Color(0xFF5CA4E6),
                        badge = "Public Username Only",
                        testTag = "home_chess_com_card",
                        onClick = onChessComClick
                    )
                }
            }

            // Session Recent Analyses Header
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.section_session_recent),
                        style = MaterialTheme.typography.titleMedium,
                        color = CreamText
                    )
                    Text(
                        text = "In-memory only",
                        style = MaterialTheme.typography.labelSmall,
                        color = SubtleStoneText
                    )
                }
            }

            if (recentReviews.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ObsidianSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.session_recent_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedStoneText
                            )
                            Text(
                                text = stringResource(R.string.session_ephemeral_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = SubtleStoneText
                            )
                        }
                    }
                }
            } else {
                items(recentReviews, key = { it.id }) { report ->
                    SessionReviewCard(
                        report = report,
                        onClick = { onOpenRecentReview(report) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StatusPill(
    icon: ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(13.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PrimaryActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    badge: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = CreamText,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedStoneText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = SubtleStoneText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SessionReviewCard(
    report: GameReviewReport,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${report.game.whitePlayer} vs ${report.game.blackPlayer}",
                    style = MaterialTheme.typography.titleSmall,
                    color = CreamText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ObsidianSurfaceHigh
                ) {
                    Text(
                        text = report.game.result,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TournamentEmerald,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = report.game.openingName ?: report.sourceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedStoneText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "W ${"%.1f".format(report.whiteAccuracy)}% • B ${"%.1f".format(report.blackAccuracy)}%",
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CreamText
                )
            }
        }
    }
}
