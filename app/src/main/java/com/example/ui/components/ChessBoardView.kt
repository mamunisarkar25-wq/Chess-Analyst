package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.analysis.MoveClassification
import com.example.chess.core.ChessBoardState
import com.example.chess.core.ChessMove
import com.example.chess.core.Piece
import com.example.chess.core.PieceColor
import com.example.chess.core.PieceType
import com.example.chess.core.Square
import com.example.ui.theme.BoardBestMoveArrow
import com.example.ui.theme.BoardCheckCrimson
import com.example.ui.theme.BoardDarkSquare
import com.example.ui.theme.BoardLastMoveDark
import com.example.ui.theme.BoardLastMoveLight
import com.example.ui.theme.BoardLegalDot
import com.example.ui.theme.BoardLightSquare
import com.example.ui.theme.BoardMaiaMoveArrow
import com.example.ui.theme.BoardSelectedSquare
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class BoardArrow(
    val from: Square,
    val to: Square,
    val color: Color
)

/**
 * Original Chess.com-inspired interactive chessboard:
 * - Warm cream (#EEEED2) & forest green (#769656) squares
 * - Smooth spring piece animations on move changes
 * - Last-move highlighting, legal-move dots & capture rings, king-in-check radial glow
 * - Coordinates inside edge squares, board flipping, engine & Maia arrows, classification badge
 */
@Composable
fun ChessBoardView(
    board: ChessBoardState,
    modifier: Modifier = Modifier,
    flipped: Boolean = false,
    showCoordinates: Boolean = true,
    lastMove: ChessMove? = null,
    lastMoveClassification: MoveClassification? = null,
    bestMoveUci: String? = null,
    maiaMoveUci: String? = null,
    interactive: Boolean = true,
    editorMode: Boolean = false,
    onMoveAttempt: ((ChessMove) -> Unit)? = null,
    onEditorSquareTap: ((Square) -> Unit)? = null
) {
    var selectedSquare by remember(board.toFen()) { mutableStateOf<Square?>(null) }
    var pendingPromotions by remember(board.toFen()) { mutableStateOf<List<ChessMove>>(emptyList()) }

    val legalMoves = remember(board.toFen(), interactive, editorMode) {
        if (interactive && !editorMode) board.generateLegalMoves() else emptyList()
    }
    val legalTargetsForSelected = remember(selectedSquare, legalMoves) {
        val sel = selectedSquare ?: return@remember emptyList<ChessMove>()
        legalMoves.filter { it.from == sel }
    }
    val checkedKingSq = remember(board.toFen()) {
        if (board.isInCheck(board.sideToMove)) board.kingSquare(board.sideToMove) else null
    }

    // Smooth animation progress for the last moved piece
    val moveAnimProgress = remember { Animatable(1f) }
    LaunchedEffect(lastMove, board.toFen()) {
        if (lastMove != null) {
            moveAnimProgress.snapTo(0f)
            moveAnimProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else {
            moveAnimProgress.snapTo(1f)
        }
    }

    val arrows = remember(bestMoveUci, maiaMoveUci) {
        buildList {
            parseUciToArrow(bestMoveUci, BoardBestMoveArrow)?.let { add(it) }
            if (maiaMoveUci != null && maiaMoveUci != bestMoveUci) {
                parseUciToArrow(maiaMoveUci, BoardMaiaMoveArrow)?.let { add(it) }
            }
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .testTag("chess_board")
            .semantics { contentDescription = "Interactive chessboard" }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(board.toFen(), flipped, interactive, editorMode, selectedSquare, legalMoves) {
                    detectTapGestures { offset ->
                        val sqSize = size.width / 8f
                        if (sqSize <= 0f) return@detectTapGestures
                        val col = (offset.x / sqSize).toInt().coerceIn(0, 7)
                        val row = (offset.y / sqSize).toInt().coerceIn(0, 7)
                        val file = if (flipped) 7 - col else col
                        val rank = if (flipped) row else 7 - row
                        val tappedSq = Square.of(file, rank)

                        if (editorMode) {
                            onEditorSquareTap?.invoke(tappedSq)
                            return@detectTapGestures
                        }

                        if (!interactive || onMoveAttempt == null) return@detectTapGestures

                        val currentSelected = selectedSquare
                        if (currentSelected != null) {
                            val matchingMoves = legalMoves.filter {
                                it.from == currentSelected && it.to == tappedSq
                            }
                            if (matchingMoves.isNotEmpty()) {
                                if (matchingMoves.size > 1 && matchingMoves.any { it.promotion != null }) {
                                    pendingPromotions = matchingMoves
                                } else {
                                    selectedSquare = null
                                    onMoveAttempt(matchingMoves.first())
                                }
                                return@detectTapGestures
                            }
                        }

                        val piece = board.pieceAt(tappedSq)
                        selectedSquare = if (piece != null && piece.color == board.sideToMove) {
                            if (selectedSquare == tappedSq) null else tappedSq
                        } else {
                            null
                        }
                    }
                }
        ) {
            val sqSize = size.width / 8f

            fun sqToTopLeft(sq: Square): Offset {
                val col = if (flipped) 7 - sq.file else sq.file
                val row = if (flipped) sq.rank else 7 - sq.rank
                return Offset(col * sqSize, row * sqSize)
            }

            // 1. Draw 64 squares & highlights
            for (rank in 0..7) {
                for (file in 0..7) {
                    val sq = Square.of(file, rank)
                    val isLight = (file + rank) % 2 == 1
                    val topLeft = sqToTopLeft(sq)
                    val isLastMoveSq = lastMove != null && (sq == lastMove.from || sq == lastMove.to)
                    val isSelected = selectedSquare == sq

                    val baseColor = when {
                        isSelected -> BoardSelectedSquare
                        isLastMoveSq -> if (isLight) BoardLastMoveLight else BoardLastMoveDark
                        else -> if (isLight) BoardLightSquare else BoardDarkSquare
                    }
                    drawRect(color = baseColor, topLeft = topLeft, size = Size(sqSize, sqSize))

                    // Check radial crimson glow
                    if (checkedKingSq == sq) {
                        val center = Offset(topLeft.x + sqSize / 2f, topLeft.y + sqSize / 2f)
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(BoardCheckCrimson, BoardCheckCrimson.copy(alpha = 0.55f), Color.Transparent),
                                center = center,
                                radius = sqSize * 0.62f
                            ),
                            topLeft = topLeft,
                            size = Size(sqSize, sqSize)
                        )
                    }
                }
            }

            // 2. Coordinates inside edge squares
            if (showCoordinates) {
                val textPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = sqSize * 0.21f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                for (i in 0..7) {
                    // Left edge rank numbers
                    val rank = if (flipped) i else 7 - i
                    val leftFile = if (flipped) 7 else 0
                    val isLightRank = (leftFile + rank) % 2 == 1
                    textPaint.color = if (isLightRank) 0xFF769656.toInt() else 0xFFEEEED2.toInt()
                    drawContext.canvas.nativeCanvas.drawText(
                        "${rank + 1}",
                        sqSize * 0.08f,
                        i * sqSize + sqSize * 0.24f,
                        textPaint
                    )

                    // Bottom edge file letters
                    val file = if (flipped) 7 - i else i
                    val bottomRank = if (flipped) 7 else 0
                    val isLightFile = (file + bottomRank) % 2 == 1
                    textPaint.color = if (isLightFile) 0xFF769656.toInt() else 0xFFEEEED2.toInt()
                    drawContext.canvas.nativeCanvas.drawText(
                        "${'a' + file}",
                        i * sqSize + sqSize * 0.77f,
                        7 * sqSize + sqSize * 0.92f,
                        textPaint
                    )
                }
            }

            // 3. Draw static pieces (skipping currently animating destination piece)
            val animT = moveAnimProgress.value
            val animatingMove = if (lastMove != null && animT < 0.999f) lastMove else null

            for (sqIdx in 0..63) {
                val piece = board.squares[sqIdx] ?: continue
                val sq = Square(sqIdx)
                if (animatingMove != null && sq == animatingMove.to) continue
                drawChessPiece(piece, sqToTopLeft(sq), sqSize)
            }

            // 4. Draw animating piece interpolated smoothly
            if (animatingMove != null) {
                val piece = board.pieceAt(animatingMove.to)
                if (piece != null) {
                    val start = sqToTopLeft(animatingMove.from)
                    val end = sqToTopLeft(animatingMove.to)
                    val lerped = Offset(
                        start.x + (end.x - start.x) * animT,
                        start.y + (end.y - start.y) * animT
                    )
                    drawChessPiece(piece, lerped, sqSize)
                }
            }

            // 5. Legal-move indicators (dot for quiet move, crisp ring for capture)
            for (move in legalTargetsForSelected) {
                val topLeft = sqToTopLeft(move.to)
                val center = Offset(topLeft.x + sqSize / 2f, topLeft.y + sqSize / 2f)
                val isCapture = move.capturedPiece != null || move.isEnPassant || board.pieceAt(move.to) != null
                if (isCapture) {
                    drawCircle(
                        color = BoardLegalDot,
                        radius = sqSize * 0.43f,
                        center = center,
                        style = Stroke(width = sqSize * 0.085f)
                    )
                } else {
                    drawCircle(
                        color = BoardLegalDot,
                        radius = sqSize * 0.16f,
                        center = center
                    )
                }
            }

            // 6. Best-move & Maia-3 arrows
            for (arrow in arrows) {
                val fromTL = sqToTopLeft(arrow.from)
                val toTL = sqToTopLeft(arrow.to)
                val start = Offset(fromTL.x + sqSize / 2f, fromTL.y + sqSize / 2f)
                val end = Offset(toTL.x + sqSize / 2f, toTL.y + sqSize / 2f)
                drawChessArrow(start, end, arrow.color, sqSize)
            }

            // 7. Classification badge on top-right corner of destination square
            if (lastMove != null && lastMoveClassification != null) {
                val destTL = sqToTopLeft(lastMove.to)
                val badgeRadius = sqSize * 0.21f
                val badgeCenter = Offset(
                    (destTL.x + sqSize - badgeRadius * 0.72f).coerceIn(badgeRadius, size.width - badgeRadius),
                    (destTL.y + badgeRadius * 0.72f).coerceIn(badgeRadius, size.height - badgeRadius)
                )
                drawCircle(color = Color(0xFF161512), radius = badgeRadius * 1.12f, center = badgeCenter)
                drawCircle(color = lastMoveClassification.badgeColor, radius = badgeRadius, center = badgeCenter)
                val badgePaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.WHITE
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = badgeRadius * 1.15f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val fm = badgePaint.fontMetrics
                val textY = badgeCenter.y - (fm.ascent + fm.descent) / 2f
                drawContext.canvas.nativeCanvas.drawText(
                    lastMoveClassification.badgeSymbol,
                    badgeCenter.x,
                    textY,
                    badgePaint
                )
            }
        }
    }

    // Pawn promotion dialog
    if (pendingPromotions.isNotEmpty()) {
        val side = board.sideToMove
        AlertDialog(
            onDismissRequest = { pendingPromotions = emptyList() },
            title = { Text("Promote Pawn", style = MaterialTheme.typography.titleLarge) },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val ordered = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
                    ordered.forEach { promoType ->
                        val move = pendingPromotions.firstOrNull { it.promotion == promoType }
                        if (move != null) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        pendingPromotions = emptyList()
                                        selectedSquare = null
                                        onMoveAttempt?.invoke(move)
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    ChessPieceIcon(piece = Piece(side, promoType), size = 44.dp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

private fun parseUciToArrow(uci: String?, color: Color): BoardArrow? {
    if (uci == null || uci.length < 4) return null
    val from = Square.fromAlgebraic(uci.substring(0, 2)) ?: return null
    val to = Square.fromAlgebraic(uci.substring(2, 4)) ?: return null
    if (from == to) return null
    return BoardArrow(from, to, color)
}

private fun DrawScope.drawChessArrow(start: Offset, end: Offset, color: Color, sqSize: Float) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
    val headLength = sqSize * 0.34f
    val headWidth = sqSize * 0.28f
    val shaftWidth = sqSize * 0.14f

    val lineEnd = Offset(
        end.x - headLength * 0.72f * cos(angle),
        end.y - headLength * 0.72f * sin(angle)
    )
    val lineStart = Offset(
        start.x + sqSize * 0.14f * cos(angle),
        start.y + sqSize * 0.14f * sin(angle)
    )

    drawLine(
        color = color,
        start = lineStart,
        end = lineEnd,
        strokeWidth = shaftWidth,
        cap = StrokeCap.Round
    )

    val tip = end
    val left = Offset(
        end.x - headLength * cos(angle) + headWidth * 0.5f * sin(angle),
        end.y - headLength * sin(angle) - headWidth * 0.5f * cos(angle)
    )
    val right = Offset(
        end.x - headLength * cos(angle) - headWidth * 0.5f * sin(angle),
        end.y - headLength * sin(angle) + headWidth * 0.5f * cos(angle)
    )
    val headPath = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(headPath, color = color)
}
