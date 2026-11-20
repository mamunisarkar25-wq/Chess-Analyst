package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.chess.core.CastlingRights
import com.example.chess.core.ChessMove
import com.example.chess.core.Piece
import com.example.chess.core.PieceColor
import com.example.chess.core.PieceType
import com.example.chess.core.Square
import com.example.chess.engine.EvalScore
import com.example.chess.engine.MaiaModelSpec
import com.example.ui.components.ChessBoardView
import com.example.ui.components.ChessPieceIcon
import com.example.ui.components.EvaluationBar
import com.example.ui.theme.ClassBlunder
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
import com.example.ui.viewmodel.FenImportUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FenImportScreen(
    state: FenImportUiState,
    showCoordinates: Boolean,
    showBestArrow: Boolean,
    showMaiaArrow: Boolean,
    installedMaiaModels: Set<MaiaModelSpec>,
    selectedMaiaModel: MaiaModelSpec,
    maiaTargetElo: Int,
    onBack: () -> Unit,
    onFenInputChange: (String) -> Unit,
    onSelectEditorPiece: (Piece?) -> Unit,
    onEditorSquareTap: (Square) -> Unit,
    onSideToMoveChange: (PieceColor) -> Unit,
    onCastlingChange: (CastlingRights) -> Unit,
    onResetBoard: () -> Unit,
    onClearBoard: () -> Unit,
    onToggleFlip: () -> Unit,
    onToggleAnalyzeMode: (Boolean) -> Unit,
    onPlayMove: (ChessMove) -> Unit,
    onUndoMove: () -> Unit,
    onOpenEngineSettings: () -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                title = { Text("FEN & Position Lab", color = CreamText) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("fen_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                            tint = CreamText
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onToggleFlip) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = stringResource(R.string.cd_flip_board),
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mode Toggle: Edit Position vs Analyze Position
                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !state.isAnalyzeMode,
                            onClick = { onToggleAnalyzeMode(false) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("fen_mode_edit")
                        ) {
                            Text("Edit Board")
                        }
                        SegmentedButton(
                            selected = state.isAnalyzeMode,
                            onClick = { onToggleAnalyzeMode(true) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            icon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("fen_mode_analyze")
                        ) {
                            Text("Analyze Position")
                        }
                    }
                }

                // Board + Optional Eval Bar
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.isAnalyzeMode) {
                            EvaluationBar(
                                score = state.evaluation?.score ?: EvalScore(0),
                                flipped = state.flipped,
                                modifier = Modifier.fillMaxHeight()
                            )
                        }
                        ChessBoardView(
                            board = state.board,
                            flipped = state.flipped,
                            showCoordinates = showCoordinates,
                            lastMove = state.lastMove,
                            bestMoveUci = if (state.isAnalyzeMode && showBestArrow) state.evaluation?.bestMoveUci else null,
                            maiaMoveUci = if (state.isAnalyzeMode && showMaiaArrow) state.maiaPrediction?.candidates?.firstOrNull()?.uci else null,
                            interactive = state.isAnalyzeMode,
                            editorMode = !state.isAnalyzeMode,
                            onMoveAttempt = onPlayMove,
                            onEditorSquareTap = onEditorSquareTap,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Validation Error Banner
                if (state.validationError != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ClassBlunder.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, ClassBlunder.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = ClassBlunder, modifier = Modifier.size(18.dp))
                                Text(state.validationError, style = MaterialTheme.typography.bodySmall, color = CreamText)
                            }
                        }
                    }
                }

                // Editor Controls OR Analysis Results
                if (!state.isAnalyzeMode) {
                    // Piece Placement Palette
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianSurface,
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Piece Palette (tap piece, then board)", style = MaterialTheme.typography.labelLarge, color = CreamText)
                                    Surface(
                                        onClick = { onSelectEditorPiece(null) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (state.isEraseTool) ClassBlunder.copy(alpha = 0.25f) else ObsidianSurfaceHigh,
                                        border = BorderStroke(1.dp, if (state.isEraseTool) ClassBlunder else ObsidianBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.Backspace, contentDescription = "Erase piece", tint = CreamText, modifier = Modifier.size(15.dp))
                                            Text("Erase", style = MaterialTheme.typography.labelMedium, color = CreamText)
                                        }
                                    }
                                }

                                PiecePaletteRow(
                                    color = PieceColor.WHITE,
                                    selected = if (state.isEraseTool) null else state.selectedEditorPiece,
                                    onSelect = onSelectEditorPiece
                                )
                                PiecePaletteRow(
                                    color = PieceColor.BLACK,
                                    selected = if (state.isEraseTool) null else state.selectedEditorPiece,
                                    onSelect = onSelectEditorPiece
                                )
                            }
                        }
                    }

                    // Side to Move + Castling Rights + Reset/Clear
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianSurface,
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Side to move", style = MaterialTheme.typography.labelLarge, color = CreamText)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = state.board.sideToMove == PieceColor.WHITE,
                                            onClick = { onSideToMoveChange(PieceColor.WHITE) },
                                            label = { Text("White") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = TournamentEmerald,
                                                selectedLabelColor = ObsidianBg
                                            )
                                        )
                                        FilterChip(
                                            selected = state.board.sideToMove == PieceColor.BLACK,
                                            onClick = { onSideToMoveChange(PieceColor.BLACK) },
                                            label = { Text("Black") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = TournamentEmerald,
                                                selectedLabelColor = ObsidianBg
                                            )
                                        )
                                    }
                                }

                                // Castling Rights Checkboxes
                                Text("Castling rights", style = MaterialTheme.typography.labelLarge, color = CreamText)
                                val c = state.board.castling
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    CastlingToggle("W O-O", c.whiteKingSide) {
                                        onCastlingChange(c.copy(whiteKingSide = it))
                                    }
                                    CastlingToggle("W O-O-O", c.whiteQueenSide) {
                                        onCastlingChange(c.copy(whiteQueenSide = it))
                                    }
                                    CastlingToggle("B O-O", c.blackKingSide) {
                                        onCastlingChange(c.copy(blackKingSide = it))
                                    }
                                    CastlingToggle("B O-O-O", c.blackQueenSide) {
                                        onCastlingChange(c.copy(blackQueenSide = it))
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onResetBoard,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, ObsidianBorder)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = CreamText)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Start Pos", color = CreamText)
                                    }
                                    OutlinedButton(
                                        onClick = onClearBoard,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, ObsidianBorder)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp), tint = CreamText)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Clear", color = CreamText)
                                    }
                                }
                            }
                        }
                    }

                    // FEN Input Box + Analyze Button
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = state.fenInput,
                                onValueChange = onFenInputChange,
                                label = { Text("FEN Notation") },
                                singleLine = false,
                                maxLines = 3,
                                textStyle = TextStyle(fontFamily = JetBrainsMonoFont, fontSize = 12.sp, color = CreamText),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = ObsidianSurface,
                                    unfocusedContainerColor = ObsidianSurface,
                                    focusedBorderColor = TournamentEmerald,
                                    unfocusedBorderColor = ObsidianBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("fen_text_input")
                            )

                            Button(
                                onClick = { onToggleAnalyzeMode(true) },
                                enabled = state.validationError == null,
                                colors = ButtonDefaults.buttonColors(containerColor = TournamentEmerald),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("fen_analyze_button")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Analyze Position", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Analyze Mode: Tactical Engine + Maia-3 Cards
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianSurface,
                            border = BorderStroke(1.dp, ObsidianBorder),
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
                                    Column {
                                        Text(
                                            text = state.evaluation?.engineName ?: "Tactical Evaluation",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TournamentEmerald
                                        )
                                        Text(
                                            text = if (state.isAnalyzing) "Searching lines..."
                                            else "Depth ${state.evaluation?.depth ?: 0} • ${state.board.sideToMove.name.lowercase().replaceFirstChar { it.uppercaseChar() }} to move",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MutedStoneText
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (state.moveHistory.isNotEmpty()) {
                                            OutlinedButton(
                                                onClick = onUndoMove,
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                border = BorderStroke(1.dp, ObsidianBorder)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo move", modifier = Modifier.size(15.dp), tint = CreamText)
                                                Spacer(Modifier.width(4.dp))
                                                Text("Undo", fontSize = 12.sp, color = CreamText)
                                            }
                                        }
                                        if (state.isAnalyzing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = TournamentEmerald
                                            )
                                        }
                                    }
                                }

                                val eval = state.evaluation
                                if (eval != null && eval.pvLines.isNotEmpty()) {
                                    eval.pvLines.forEach { pv ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = ObsidianSurfaceHigh,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (pv.multiPvIndex == 1) TournamentEmerald else ObsidianBg
                                                ) {
                                                    Text(
                                                        text = pv.score.formatDisplay(),
                                                        fontFamily = JetBrainsMonoFont,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (pv.multiPvIndex == 1) ObsidianBg else CreamText,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                                Text(
                                                    text = pv.sanMoves.joinToString(" "),
                                                    fontFamily = JetBrainsMonoFont,
                                                    fontSize = 12.sp,
                                                    color = CreamText,
                                                    maxLines = 2
                                                )
                                            }
                                        }
                                    }
                                } else if (!state.isAnalyzing) {
                                    Text(
                                        text = "No legal moves available in this position.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MutedStoneText
                                    )
                                }
                            }
                        }
                    }

                    // Maia-3 Human-Like Recommendations Card
                    item {
                        MaiaHumanInsightCard(
                            prediction = state.maiaPrediction,
                            isInstalled = selectedMaiaModel in installedMaiaModels,
                            selectedModel = selectedMaiaModel,
                            targetElo = maiaTargetElo,
                            onOpenModelManager = onOpenEngineSettings
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun PiecePaletteRow(
    color: PieceColor,
    selected: Piece?,
    onSelect: (Piece) -> Unit
) {
    val types = listOf(
        PieceType.KING, PieceType.QUEEN, PieceType.ROOK,
        PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        types.forEach { type ->
            val piece = Piece(color, type)
            val isSel = selected == piece
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSel) TournamentEmerald.copy(alpha = 0.25f) else ObsidianSurfaceHigh)
                    .border(
                        width = if (isSel) 2.dp else 1.dp,
                        color = if (isSel) TournamentEmerald else ObsidianBorder,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(piece) },
                contentAlignment = Alignment.Center
            ) {
                ChessPieceIcon(piece = piece, size = 38.dp)
            }
        }
    }
}

@Composable
private fun CastlingToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = TournamentEmerald)
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = CreamText)
    }
}

@Composable
fun MaiaHumanInsightCard(
    prediction: com.example.chess.engine.MaiaPrediction?,
    isInstalled: Boolean,
    selectedModel: MaiaModelSpec,
    targetElo: Int,
    onOpenModelManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
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
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(20.dp))
                    Column {
                        Text(
                            text = "Maia-3 Human-Like Moves",
                            style = MaterialTheme.typography.titleSmall,
                            color = GoldAccent
                        )
                        Text(
                            text = if (isInstalled) "${selectedModel.displayName} • Target $targetElo Elo"
                            else "Optional local neural model • Not downloaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedStoneText
                        )
                    }
                }
                OutlinedButton(
                    onClick = onOpenModelManager,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(if (isInstalled) "Configure" else "Download", fontSize = 12.sp, color = GoldAccent)
                }
            }

            if (!isInstalled) {
                Text(
                    text = "Download an optional Maia-3 ONNX model (7–158 MB) to predict realistic human moves and move probabilities locally on-device. Stockfish remains your tactical evaluator.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtleStoneText
                )
            } else if (prediction == null) {
                Text(
                    text = "Computing human move distribution...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedStoneText
                )
            } else {
                prediction.candidates.forEach { cand ->
                    val pct = (cand.probability * 100f).coerceIn(0f, 100f)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = cand.san,
                                fontFamily = JetBrainsMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = CreamText
                            )
                            Text(
                                text = "${"%.1f".format(pct)}%",
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 12.sp,
                                color = GoldAccent
                            )
                        }
                        LinearProgressIndicator(
                            progress = { cand.probability.coerceIn(0f, 1f) },
                            color = GoldAccent,
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
    }
}
