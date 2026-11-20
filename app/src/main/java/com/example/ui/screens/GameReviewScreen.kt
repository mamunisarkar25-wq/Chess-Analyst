package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.chess.analysis.AnalyzedMove
import com.example.chess.analysis.GameAnalyzer
import com.example.chess.analysis.GameReviewReport
import com.example.chess.analysis.MoveClassification
import com.example.chess.analysis.PhaseAnalysisSummary
import com.example.chess.analysis.PlayerPhaseStats
import com.example.chess.core.ChessBoardState
import com.example.chess.core.ChessMove
import com.example.chess.core.PieceColor
import com.example.chess.engine.EvalScore
import com.example.chess.engine.MaiaModelSpec
import com.example.ui.components.ChessBoardView
import com.example.ui.components.EvaluationBar
import com.example.ui.components.EvaluationGraph
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
import com.example.ui.viewmodel.GameReviewUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameReviewScreen(
    state: GameReviewUiState,
    currentBoard: ChessBoardState,
    showCoordinates: Boolean,
    showBestArrow: Boolean,
    showMaiaArrow: Boolean,
    installedMaiaModels: Set<MaiaModelSpec>,
    selectedMaiaModel: MaiaModelSpec,
    maiaTargetElo: Int,
    onBack: () -> Unit,
    onCancelAnalysis: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onToggleFlip: () -> Unit,
    onJumpToPly: (Int) -> Unit,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
    onPlayVariationMove: (ChessMove) -> Unit,
    onClearVariation: () -> Unit,
    onExportAnnotatedPgn: () -> String?,
    onOpenEngineSettings: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var showMethodologyDialog by remember { mutableStateOf(false) }

    val game = state.activeGame
    val report = state.report
    val inVariation = state.variationSteps.isNotEmpty()
    val currentAnalyzedMove: AnalyzedMove? = if (!inVariation && state.currentPly > 0) {
        report?.moves?.getOrNull(state.currentPly - 1)
    } else null

    val lastMove: ChessMove? = if (inVariation) {
        state.variationSteps.last().move
    } else if (state.currentPly > 0) {
        game?.moves?.getOrNull(state.currentPly - 1)?.move
    } else null

    val displayedEval: EvalScore = when {
        inVariation -> state.variationEvaluation?.score ?: currentAnalyzedMove?.evalAfter ?: EvalScore(0)
        currentAnalyzedMove != null -> currentAnalyzedMove.evalAfter
        report != null -> report.initialEval
        else -> EvalScore(0)
    }

    val bestArrowUci: String? = if (!showBestArrow) null else when {
        inVariation -> state.variationEvaluation?.bestMoveUci
        currentAnalyzedMove != null && currentAnalyzedMove.bestAlternativeUci != currentAnalyzedMove.node.uci ->
            currentAnalyzedMove.bestAlternativeUci
        else -> null
    }

    val maiaArrowUci: String? = if (!showMaiaArrow) null else state.currentMaiaPrediction?.candidates?.firstOrNull()?.uci

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Game Review", color = CreamText, style = MaterialTheme.typography.titleMedium)
                        if (game?.openingName != null) {
                            Text(
                                text = game.openingName,
                                color = MutedStoneText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("review_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                            tint = CreamText
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMethodologyDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.cd_methodology_info),
                            tint = MutedStoneText
                        )
                    }
                    if (report != null) {
                        IconButton(
                            onClick = {
                                val pgnText = onExportAnnotatedPgn() ?: return@IconButton
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "${game?.whitePlayer} vs ${game?.blackPlayer} - Chess Analyst Review")
                                    putExtra(Intent.EXTRA_TEXT, pgnText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Export Annotated PGN"))
                            },
                            modifier = Modifier.testTag("review_share_pgn_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.cd_share_pgn),
                                tint = TournamentEmerald
                            )
                        }
                    }
                    IconButton(onClick = onOpenEngineSettings) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = stringResource(R.string.cd_engine_settings),
                            tint = CreamText
                        )
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
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Live Analysis Progress Bar with Cancel button
                if (state.isAnalyzing && state.progress != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurface,
                            border = BorderStroke(1.dp, TournamentEmerald.copy(alpha = 0.45f)),
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
                                    Text(
                                        text = "${state.progress.statusText} (${state.progress.currentPly}/${state.progress.totalPlies})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CreamText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = onCancelAnalysis,
                                        modifier = Modifier.testTag("review_cancel_analysis")
                                    ) {
                                        Text("Cancel", color = ClassBlunder, fontWeight = FontWeight.Bold)
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { state.progress.fraction },
                                    color = TournamentEmerald,
                                    trackColor = ObsidianSurfaceHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }

                // Error Message
                if (state.errorMessage != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ClassBlunder.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, ClassBlunder.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = CreamText,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                // Top Player Bar (Black when not flipped, White when flipped)
                if (game != null) {
                    item {
                        val topIsWhite = state.flipped
                        PlayerNameBar(
                            name = if (topIsWhite) game.whitePlayer else game.blackPlayer,
                            elo = if (topIsWhite) game.whiteElo else game.blackElo,
                            isWhite = topIsWhite,
                            accuracy = if (topIsWhite) report?.whiteAccuracy else report?.blackAccuracy
                        )
                    }
                }

                // Eval Bar + Interactive Chessboard
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EvaluationBar(
                            score = displayedEval,
                            flipped = state.flipped,
                            modifier = Modifier.fillMaxHeight()
                        )
                        ChessBoardView(
                            board = currentBoard,
                            flipped = state.flipped,
                            showCoordinates = showCoordinates,
                            lastMove = lastMove,
                            lastMoveClassification = currentAnalyzedMove?.classification,
                            bestMoveUci = bestArrowUci,
                            maiaMoveUci = maiaArrowUci,
                            interactive = !state.isAnalyzing,
                            onMoveAttempt = onPlayVariationMove,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Bottom Player Bar
                if (game != null) {
                    item {
                        val bottomIsWhite = !state.flipped
                        PlayerNameBar(
                            name = if (bottomIsWhite) game.whitePlayer else game.blackPlayer,
                            elo = if (bottomIsWhite) game.whiteElo else game.blackElo,
                            isWhite = bottomIsWhite,
                            accuracy = if (bottomIsWhite) report?.whiteAccuracy else report?.blackAccuracy
                        )
                    }
                }

                // Interactive Variation Exploration Banner
                if (inVariation) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GoldAccent.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Exploring Variation (${state.variationEvaluation?.score?.formatDisplay() ?: if (state.isEvaluatingVariation) "..." else "—"})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = GoldAccent
                                    )
                                    Text(
                                        text = state.variationSteps.joinToString(" ") { it.san },
                                        fontFamily = JetBrainsMonoFont,
                                        fontSize = 12.sp,
                                        color = CreamText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                OutlinedButton(
                                    onClick = onClearVariation,
                                    border = BorderStroke(1.dp, GoldAccent),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Back to Game", fontSize = 11.sp, color = GoldAccent)
                                }
                            }
                        }
                    }
                }

                // Move Navigation Bar
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurface,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onJumpToPly(0) }, modifier = Modifier.testTag("nav_first_move")) {
                                Icon(Icons.Default.FirstPage, contentDescription = "Start of game", tint = CreamText)
                            }
                            IconButton(onClick = onStepBack, modifier = Modifier.testTag("nav_prev_move")) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous move", tint = CreamText)
                            }
                            Text(
                                text = "${state.currentPly} / ${game?.moves?.size ?: 0}",
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 12.sp,
                                color = MutedStoneText
                            )
                            IconButton(onClick = onStepForward, modifier = Modifier.testTag("nav_next_move")) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next move", tint = CreamText)
                            }
                            IconButton(
                                onClick = { onJumpToPly(game?.moves?.size ?: 0) },
                                modifier = Modifier.testTag("nav_last_move")
                            ) {
                                Icon(Icons.Default.LastPage, contentDescription = "End of game", tint = CreamText)
                            }
                            IconButton(onClick = onToggleFlip, modifier = Modifier.testTag("nav_flip_board")) {
                                Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.cd_flip_board), tint = TournamentEmerald)
                            }
                        }
                    }
                }

                // Current Move Explanation Card
                if (currentAnalyzedMove != null) {
                    item {
                        CurrentMoveDetailCard(move = currentAnalyzedMove)
                    }
                }

                // Review / Moves / Phases Tabs
                if (report != null) {
                    item {
                        val tabs = listOf("Review", "Moves", "Phases")
                        TabRow(
                            selectedTabIndex = state.selectedTab,
                            containerColor = ObsidianSurface,
                            contentColor = TournamentEmerald,
                            indicator = { positions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(positions[state.selectedTab]),
                                    color = TournamentEmerald
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            tabs.forEachIndexed { idx, title ->
                                Tab(
                                    selected = state.selectedTab == idx,
                                    onClick = { onSelectTab(idx) },
                                    text = {
                                        Text(
                                            text = title,
                                            fontWeight = if (state.selectedTab == idx) FontWeight.Bold else FontWeight.Medium,
                                            color = if (state.selectedTab == idx) TournamentEmerald else MutedStoneText
                                        )
                                    },
                                    modifier = Modifier.testTag("review_tab_$idx")
                                )
                            }
                        }
                    }

                    when (state.selectedTab) {
                        0 -> {
                            // Review Summary: Accuracy, Estimated Elo, Eval Graph, Maia-3, Classification Table
                            item {
                                AccuracyAndRatingRow(report = report)
                            }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Evaluation Graph (tap any move)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MutedStoneText
                                    )
                                    EvaluationGraph(
                                        initialEval = report.initialEval,
                                        moves = report.moves,
                                        currentPly = state.currentPly,
                                        onSelectPly = onJumpToPly
                                    )
                                }
                            }
                            item {
                                MaiaHumanInsightCard(
                                    prediction = state.currentMaiaPrediction,
                                    isInstalled = selectedMaiaModel in installedMaiaModels,
                                    selectedModel = selectedMaiaModel,
                                    targetElo = maiaTargetElo,
                                    onOpenModelManager = onOpenEngineSettings
                                )
                            }
                            item {
                                ClassificationSummaryCard(report = report)
                            }
                        }

                        1 -> {
                            // Two-column Algebraic Move Sheet
                            item {
                                MoveListCard(
                                    moves = report.moves,
                                    currentPly = state.currentPly,
                                    onSelectPly = onJumpToPly
                                )
                            }
                        }

                        2 -> {
                            // Game Phase Analysis (OPENING / MIDDLEGAME / ENDGAME)
                            report.phaseSummaries.forEach { summary ->
                                item(key = summary.phase.name) {
                                    PhaseSummaryCard(
                                        summary = summary,
                                        whiteName = report.game.whitePlayer,
                                        blackName = report.game.blackPlayer,
                                        onJumpToPly = onJumpToPly
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    if (showMethodologyDialog) {
        AlertDialog(
            onDismissRequest = { showMethodologyDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("Documented Review Algorithm", color = CreamText) },
            text = {
                Text(
                    text = GameAnalyzer.METHODOLOGY_SUMMARY,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedStoneText
                )
            },
            confirmButton = {
                TextButton(onClick = { showMethodologyDialog = false }) {
                    Text("Got it", color = TournamentEmerald)
                }
            }
        )
    }
}

@Composable
private fun PlayerNameBar(
    name: String,
    elo: Int?,
    isWhite: Boolean,
    accuracy: Double?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isWhite) Color(0xFFF3F1EC) else Color(0xFF33312D))
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = CreamText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (elo != null) {
                Text(
                    text = "($elo)",
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 12.sp,
                    color = SubtleStoneText
                )
            }
        }
        if (accuracy != null) {
            Surface(shape = RoundedCornerShape(6.dp), color = ObsidianSurfaceHigh) {
                Text(
                    text = "${"%.1f".format(accuracy)}%",
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TournamentEmerald,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun CurrentMoveDetailCard(move: AnalyzedMove) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, move.classification.badgeColor.copy(alpha = 0.55f)),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(move.classification.badgeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = move.classification.badgeSymbol,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "${move.node.moveNumber}${if (move.node.side == PieceColor.WHITE) "." else "..."} ${move.node.san} — ${move.classification.label}",
                        style = MaterialTheme.typography.titleSmall,
                        color = CreamText,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(shape = RoundedCornerShape(6.dp), color = ObsidianSurfaceHigh) {
                    Text(
                        text = "${move.evalAfterDisplay} • ${move.phase.displayTitle}",
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 11.sp,
                        color = MutedStoneText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = move.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = CreamText
            )

            if (move.bestLineSan.isNotEmpty() && move.bestAlternativeSan != move.node.san) {
                Surface(shape = RoundedCornerShape(8.dp), color = ObsidianSurfaceHigh) {
                    Text(
                        text = "Best line: ${move.bestLineSan.take(6).joinToString(" ")}",
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 11.5.sp,
                        color = TournamentEmerald,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccuracyAndRatingRow(report: GameReviewReport) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PlayerAccuracyCard(
            title = report.game.whitePlayer,
            isWhite = true,
            accuracy = report.whiteAccuracy,
            acpl = report.whiteAcpl,
            estElo = report.whiteEstimatedElo,
            modifier = Modifier.weight(1f)
        )
        PlayerAccuracyCard(
            title = report.game.blackPlayer,
            isWhite = false,
            accuracy = report.blackAccuracy,
            acpl = report.blackAcpl,
            estElo = report.blackEstimatedElo,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlayerAccuracyCard(
    title: String,
    isWhite: Boolean,
    accuracy: Double,
    acpl: Double,
    estElo: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isWhite) Color(0xFFF3F1EC) else Color(0xFF45423D))
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedStoneText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${"%.1f".format(accuracy)}%",
                fontFamily = JetBrainsMonoFont,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = CreamText
            )
            Text(
                text = "Est. Rating: $estElo",
                fontFamily = JetBrainsMonoFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TournamentEmerald
            )
            Text(
                text = "Avg CPL: ${"%.0f".format(acpl)}",
                fontFamily = JetBrainsMonoFont,
                fontSize = 11.sp,
                color = SubtleStoneText
            )
        }
    }
}

@Composable
private fun ClassificationSummaryCard(report: GameReviewReport) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Move Classifications", style = MaterialTheme.typography.titleSmall, color = CreamText)
                Text("White  /  Black", style = MaterialTheme.typography.labelSmall, color = SubtleStoneText)
            }
            MoveClassification.entries.forEach { cls ->
                val wCount = report.whiteClassificationCounts[cls] ?: 0
                val bCount = report.blackClassificationCounts[cls] ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(cls.badgeColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(cls.badgeSymbol, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text(cls.label, style = MaterialTheme.typography.bodyMedium, color = CreamText)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(
                            text = wCount.toString(),
                            fontFamily = JetBrainsMonoFont,
                            fontWeight = FontWeight.Bold,
                            color = if (wCount > 0) cls.badgeColor else SubtleStoneText
                        )
                        Text(
                            text = bCount.toString(),
                            fontFamily = JetBrainsMonoFont,
                            fontWeight = FontWeight.Bold,
                            color = if (bCount > 0) cls.badgeColor else SubtleStoneText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveListCard(
    moves: List<AnalyzedMove>,
    currentPly: Int,
    onSelectPly: (Int) -> Unit
) {
    val pairs = mutableListOf<Pair<AnalyzedMove?, AnalyzedMove?>>()
    var i = 0
    while (i < moves.size) {
        val m = moves[i]
        if (m.node.side == PieceColor.WHITE) {
            val next = moves.getOrNull(i + 1)?.takeIf { it.node.side == PieceColor.BLACK }
            pairs.add(m to next)
            i += if (next != null) 2 else 1
        } else {
            pairs.add(null to m)
            i += 1
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            pairs.forEach { (whiteMove, blackMove) ->
                val moveNum = whiteMove?.node?.moveNumber ?: blackMove?.node?.moveNumber ?: 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$moveNum.",
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 12.sp,
                        color = SubtleStoneText,
                        modifier = Modifier.width(34.dp)
                    )
                    MoveCell(
                        move = whiteMove,
                        selected = whiteMove?.node?.plyIndex == currentPly,
                        onClick = { whiteMove?.let { onSelectPly(it.node.plyIndex) } },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    MoveCell(
                        move = blackMove,
                        selected = blackMove?.node?.plyIndex == currentPly,
                        onClick = { blackMove?.let { onSelectPly(it.node.plyIndex) } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MoveCell(
    move: AnalyzedMove?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (move == null) {
        Spacer(modifier = modifier.height(32.dp))
        return
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) TournamentEmerald.copy(alpha = 0.24f) else ObsidianSurfaceHigh,
        border = if (selected) BorderStroke(1.dp, TournamentEmerald) else null,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(move.classification.badgeColor)
                )
                Text(
                    text = move.node.san,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CreamText
                )
            }
            Text(
                text = move.evalAfterDisplay,
                fontFamily = JetBrainsMonoFont,
                fontSize = 10.5.sp,
                color = SubtleStoneText
            )
        }
    }
}

@Composable
private fun PhaseSummaryCard(
    summary: PhaseAnalysisSummary,
    whiteName: String,
    blackName: String,
    onJumpToPly: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("phase_card_${summary.phase.name.lowercase()}")
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
                Text(
                    text = summary.phase.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = TournamentEmerald,
                    fontWeight = FontWeight.Bold
                )
                if (summary.reached && summary.startPly != null && summary.endPly != null) {
                    Surface(
                        onClick = { onJumpToPly(summary.startPly) },
                        shape = RoundedCornerShape(6.dp),
                        color = ObsidianSurfaceHigh
                    ) {
                        Text(
                            text = "Moves ${(summary.startPly + 1) / 2}–${(summary.endPly + 1) / 2} • Jump",
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 11.sp,
                            color = CreamText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (!summary.reached) {
                Text(
                    text = stringResource(R.string.phase_not_reached),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedStoneText
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PhasePlayerColumn(
                        playerName = whiteName,
                        sideLabel = "White",
                        stats = summary.whiteStats,
                        modifier = Modifier.weight(1f)
                    )
                    PhasePlayerColumn(
                        playerName = blackName,
                        sideLabel = "Black",
                        stats = summary.blackStats,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PhasePlayerColumn(
    playerName: String,
    sideLabel: String,
    stats: PlayerPhaseStats?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ObsidianSurfaceHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$sideLabel • $playerName",
                style = MaterialTheme.typography.labelSmall,
                color = MutedStoneText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (stats == null) {
                Text("No moves played", style = MaterialTheme.typography.bodySmall, color = SubtleStoneText)
            } else {
                Text(
                    text = "${"%.1f".format(stats.accuracy)}%",
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = CreamText
                )
                Text(
                    text = "${stats.moveCount} moves • ${"%.0f".format(stats.avgCentipawnLoss)} ACPL",
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 11.sp,
                    color = MutedStoneText
                )
                Text(
                    text = "Best+: ${stats.bestOrBetterCount} • Errors: ${stats.mistakesAndBlundersCount}",
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 11.sp,
                    color = SubtleStoneText
                )
            }
        }
    }
}
