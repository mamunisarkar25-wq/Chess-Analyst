package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.chess.core.PieceColor
import com.example.data.chesscom.ChessComPlayerSummary
import com.example.data.chesscom.ChessComRecentGame
import com.example.data.chesscom.PerspectiveResult
import com.example.data.chesscom.TimeControlCategory
import com.example.ui.theme.ClassBlunder
import com.example.ui.theme.CreamText
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.MutedStoneText
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.ResultDrawBg
import com.example.ui.theme.ResultDrawFg
import com.example.ui.theme.ResultLossBg
import com.example.ui.theme.ResultLossFg
import com.example.ui.theme.ResultWinBg
import com.example.ui.theme.ResultWinFg
import com.example.ui.theme.SubtleStoneText
import com.example.ui.theme.TournamentEmerald
import com.example.ui.viewmodel.ChessComUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessComScreen(
    state: ChessComUiState,
    onBack: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onFetchGames: () -> Unit,
    onAnalyzeGame: (ChessComRecentGame) -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                title = { Text("Chess.com Games", color = CreamText) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chesscom_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                            tint = CreamText
                        )
                    }
                },
                actions = {
                    if (state.player != null) {
                        IconButton(
                            onClick = onFetchGames,
                            enabled = !state.isLoading,
                            modifier = Modifier.testTag("chesscom_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh recent games",
                                tint = TournamentEmerald
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBg)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Public Username Connection Card (Zero password / no login)
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ObsidianSurface,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null, tint = TournamentEmerald, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Public username only — never asks for a password",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TournamentEmerald
                                )
                            }

                            OutlinedTextField(
                                value = state.usernameInput,
                                onValueChange = onUsernameChange,
                                label = { Text("Chess.com Username") },
                                placeholder = { Text("e.g., Hikaru, MagnusCarlsen, GothamChess") },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MutedStoneText) },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onFetchGames() }),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = ObsidianBg,
                                    unfocusedContainerColor = ObsidianBg,
                                    focusedBorderColor = TournamentEmerald,
                                    unfocusedBorderColor = ObsidianBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("chesscom_username_input")
                            )

                            Button(
                                onClick = onFetchGames,
                                enabled = !state.isLoading && state.usernameInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = TournamentEmerald),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("chesscom_fetch_button")
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = ObsidianBg
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("Fetching latest games...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Fetch Latest 10 Games", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Error Banner
                if (state.errorMessage != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ClassBlunder.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, ClassBlunder.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = ClassBlunder)
                                Text(state.errorMessage, style = MaterialTheme.typography.bodySmall, color = CreamText)
                            }
                        }
                    }
                }

                // Connected Player Summary Banner
                if (state.player != null) {
                    item {
                        PlayerProfileBanner(player = state.player)
                    }
                }

                // Recent Games List Header
                if (state.player != null && !state.isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Latest ${state.games.size} Games",
                                style = MaterialTheme.typography.titleMedium,
                                color = CreamText
                            )
                            Text(
                                text = "Sorted by played date",
                                style = MaterialTheme.typography.labelSmall,
                                color = SubtleStoneText
                            )
                        }
                    }
                }

                if (state.hasSearched && !state.isLoading && state.games.isEmpty() && state.errorMessage == null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianSurface,
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No recent public standard chess games found for this player.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedStoneText,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                }

                itemsIndexed(state.games, key = { _, g -> g.id }) { idx, game ->
                    ChessComGameCard(
                        game = game,
                        index = idx,
                        onAnalyzeClick = { onAnalyzeGame(game) }
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun PlayerProfileBanner(player: ChessComPlayerSummary) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurfaceHigh,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (player.avatarUrl != null) {
                AsyncImage(
                    model = player.avatarUrl,
                    contentDescription = "${player.username} avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(ObsidianBg)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(TournamentEmerald.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = TournamentEmerald)
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (player.title != null) {
                        Surface(shape = RoundedCornerShape(4.dp), color = ClassBlunder) {
                            Text(
                                text = player.title,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = player.username,
                        style = MaterialTheme.typography.titleMedium,
                        color = CreamText,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    player.rapidRating?.let { RatingChip("Rapid", it) }
                    player.blitzRating?.let { RatingChip("Blitz", it) }
                    player.bulletRating?.let { RatingChip("Bullet", it) }
                }
            }
        }
    }
}

@Composable
private fun RatingChip(label: String, rating: Int) {
    Text(
        text = "$label $rating",
        fontFamily = JetBrainsMonoFont,
        fontSize = 11.sp,
        color = MutedStoneText
    )
}

@Composable
private fun ChessComGameCard(
    game: ChessComRecentGame,
    index: Int,
    onAnalyzeClick: () -> Unit
) {
    val (resBg, resFg) = when (game.result) {
        PerspectiveResult.WIN -> ResultWinBg to ResultWinFg
        PerspectiveResult.LOSS -> ResultLossBg to ResultLossFg
        PerspectiveResult.DRAW -> ResultDrawBg to ResultDrawFg
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chesscom_game_card_$index")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Opponent + Ratings + Result Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (game.playerSide == PieceColor.WHITE) Color(0xFFF3F1EC) else Color(0xFF45423D))
                        )
                        Text(
                            text = "vs ${game.opponentUsername}",
                            style = MaterialTheme.typography.titleSmall,
                            color = CreamText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = buildString {
                            append("You: ${game.playerRating ?: "?"} (${if (game.playerSide == PieceColor.WHITE) "White" else "Black"})")
                            append("  •  Opp: ${game.opponentRating ?: "?"}")
                        },
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 11.5.sp,
                        color = MutedStoneText
                    )
                }

                Spacer(Modifier.width(8.dp))

                Surface(shape = RoundedCornerShape(8.dp), color = resBg) {
                    Text(
                        text = game.result.label,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = resFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Middle Row: Time Control + Date + Opening
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(shape = RoundedCornerShape(6.dp), color = ObsidianSurfaceHigh) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (game.timeCategory == TimeControlCategory.BULLET || game.timeCategory == TimeControlCategory.BLITZ)
                                    Icons.Default.Bolt else Icons.Default.Timer,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${game.timeControlDisplay} • ${game.timeCategory.label}",
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 11.sp,
                                color = CreamText
                            )
                        }
                    }
                    if (game.formattedDate.isNotBlank()) {
                        Text(
                            text = game.formattedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = SubtleStoneText
                        )
                    }
                }
            }

            if (game.openingName != null) {
                Text(
                    text = game.openingName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedStoneText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Bottom Action: Reason + Analyze Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = game.resultReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtleStoneText
                )
                Button(
                    onClick = onAnalyzeClick,
                    colors = ButtonDefaults.buttonColors(containerColor = TournamentEmerald),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("chesscom_analyze_btn_$index")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Analyze", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
