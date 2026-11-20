package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.chess.core.Piece
import com.example.chess.core.PieceColor
import com.example.chess.core.PieceType

private val WhiteFill = Color(0xFFFAF8F0)
private val WhiteShadow = Color(0xFFE0DAC8)
private val WhiteStroke = Color(0xFF1F1D1A)

private val BlackFill = Color(0xFF23211E)
private val BlackHighlight = Color(0xFF5C574F)
private val BlackStroke = Color(0xFF11100E)

/**
 * Original traditional Staunton-inspired vector chess pieces drawn on Jetpack Compose Canvas.
 * Crisp at every resolution and high contrast on both cream and forest-green tournament squares.
 */
@Composable
fun ChessPieceIcon(
    piece: Piece,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Canvas(modifier = modifier.size(size)) {
        drawChessPiece(piece, Offset.Zero, this.size.minDimension)
    }
}

fun DrawScope.drawChessPiece(piece: Piece, topLeft: Offset, squareSize: Float) {
    val fill = if (piece.color == PieceColor.WHITE) WhiteFill else BlackFill
    val accent = if (piece.color == PieceColor.WHITE) WhiteShadow else BlackHighlight
    val strokeColor = if (piece.color == PieceColor.WHITE) WhiteStroke else BlackStroke
    val detailStroke = if (piece.color == PieceColor.WHITE) WhiteStroke else Color(0xFFCCC6BA)
    val strokeWidth = (squareSize * 0.036f).coerceAtLeast(1.2f)
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val thinStroke = Stroke(width = strokeWidth * 0.72f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun p(x: Float, y: Float) = Offset(topLeft.x + x * squareSize, topLeft.y + y * squareSize)

    // Subtle ground shadow for physical depth
    drawOval(
        color = Color(0x28000000),
        topLeft = p(0.18f, 0.855f),
        size = Size(squareSize * 0.64f, squareSize * 0.09f)
    )

    when (piece.type) {
        PieceType.PAWN -> {
            // Base + flared body
            val body = Path().apply {
                moveTo(p(0.24f, 0.87f).x, p(0.24f, 0.87f).y)
                lineTo(p(0.76f, 0.87f).x, p(0.76f, 0.87f).y)
                lineTo(p(0.72f, 0.77f).x, p(0.72f, 0.77f).y)
                cubicTo(
                    p(0.64f, 0.72f).x, p(0.64f, 0.72f).y,
                    p(0.58f, 0.60f).x, p(0.58f, 0.60f).y,
                    p(0.57f, 0.49f).x, p(0.57f, 0.49f).y
                )
                lineTo(p(0.43f, 0.49f).x, p(0.43f, 0.49f).y)
                cubicTo(
                    p(0.42f, 0.60f).x, p(0.42f, 0.60f).y,
                    p(0.36f, 0.72f).x, p(0.36f, 0.72f).y,
                    p(0.28f, 0.77f).x, p(0.28f, 0.77f).y
                )
                close()
            }
            drawPath(body, fill, style = Fill)
            drawPath(body, strokeColor, style = stroke)

            // Collar
            drawRoundRect(
                color = fill,
                topLeft = p(0.35f, 0.43f),
                size = Size(squareSize * 0.30f, squareSize * 0.075f),
                cornerRadius = CornerRadius(squareSize * 0.04f)
            )
            drawRoundRect(
                color = strokeColor,
                topLeft = p(0.35f, 0.43f),
                size = Size(squareSize * 0.30f, squareSize * 0.075f),
                cornerRadius = CornerRadius(squareSize * 0.04f),
                style = stroke
            )

            // Head sphere
            drawCircle(color = fill, radius = squareSize * 0.145f, center = p(0.50f, 0.31f))
            drawCircle(color = strokeColor, radius = squareSize * 0.145f, center = p(0.50f, 0.31f), style = stroke)
            // Base trim line
            drawLine(detailStroke, p(0.29f, 0.79f), p(0.71f, 0.79f), strokeWidth * 0.65f)
        }

        PieceType.ROOK -> {
            val rook = Path().apply {
                moveTo(p(0.21f, 0.87f).x, p(0.21f, 0.87f).y)
                lineTo(p(0.79f, 0.87f).x, p(0.79f, 0.87f).y)
                lineTo(p(0.76f, 0.76f).x, p(0.76f, 0.76f).y)
                lineTo(p(0.68f, 0.71f).x, p(0.68f, 0.71f).y)
                lineTo(p(0.66f, 0.39f).x, p(0.66f, 0.39f).y)
                lineTo(p(0.74f, 0.32f).x, p(0.74f, 0.32f).y)
                lineTo(p(0.74f, 0.17f).x, p(0.74f, 0.17f).y)
                lineTo(p(0.63f, 0.17f).x, p(0.63f, 0.17f).y)
                lineTo(p(0.63f, 0.25f).x, p(0.63f, 0.25f).y)
                lineTo(p(0.555f, 0.25f).x, p(0.555f, 0.25f).y)
                lineTo(p(0.555f, 0.17f).x, p(0.555f, 0.17f).y)
                lineTo(p(0.445f, 0.17f).x, p(0.445f, 0.17f).y)
                lineTo(p(0.445f, 0.25f).x, p(0.445f, 0.25f).y)
                lineTo(p(0.37f, 0.25f).x, p(0.37f, 0.25f).y)
                lineTo(p(0.37f, 0.17f).x, p(0.37f, 0.17f).y)
                lineTo(p(0.26f, 0.17f).x, p(0.26f, 0.17f).y)
                lineTo(p(0.26f, 0.32f).x, p(0.26f, 0.32f).y)
                lineTo(p(0.34f, 0.39f).x, p(0.34f, 0.39f).y)
                lineTo(p(0.32f, 0.71f).x, p(0.32f, 0.71f).y)
                lineTo(p(0.24f, 0.76f).x, p(0.24f, 0.76f).y)
                close()
            }
            drawPath(rook, fill, style = Fill)
            drawPath(rook, strokeColor, style = stroke)
            drawLine(detailStroke, p(0.34f, 0.39f), p(0.66f, 0.39f), strokeWidth * 0.7f)
            drawLine(detailStroke, p(0.32f, 0.71f), p(0.68f, 0.71f), strokeWidth * 0.7f)
            drawLine(detailStroke, p(0.25f, 0.79f), p(0.75f, 0.79f), strokeWidth * 0.65f)
        }

        PieceType.KNIGHT -> {
            // Classic Staunton horse head silhouette
            val knight = Path().apply {
                moveTo(p(0.22f, 0.87f).x, p(0.22f, 0.87f).y)
                lineTo(p(0.79f, 0.87f).x, p(0.79f, 0.87f).y)
                lineTo(p(0.76f, 0.76f).x, p(0.76f, 0.76f).y)
                // Back mane curve
                cubicTo(
                    p(0.78f, 0.52f).x, p(0.78f, 0.52f).y,
                    p(0.72f, 0.28f).x, p(0.72f, 0.28f).y,
                    p(0.52f, 0.18f).x, p(0.52f, 0.18f).y
                )
                // Ear
                lineTo(p(0.45f, 0.12f).x, p(0.45f, 0.12f).y)
                lineTo(p(0.42f, 0.21f).x, p(0.42f, 0.21f).y)
                // Forehead to muzzle
                cubicTo(
                    p(0.31f, 0.26f).x, p(0.31f, 0.26f).y,
                    p(0.21f, 0.39f).x, p(0.21f, 0.39f).y,
                    p(0.20f, 0.50f).x, p(0.20f, 0.50f).y
                )
                lineTo(p(0.27f, 0.55f).x, p(0.27f, 0.55f).y)
                // Jaw & throat
                cubicTo(
                    p(0.35f, 0.52f).x, p(0.35f, 0.52f).y,
                    p(0.44f, 0.45f).x, p(0.44f, 0.45f).y,
                    p(0.48f, 0.46f).x, p(0.48f, 0.46f).y
                )
                // Chest sweep to base
                cubicTo(
                    p(0.44f, 0.58f).x, p(0.44f, 0.58f).y,
                    p(0.30f, 0.65f).x, p(0.30f, 0.65f).y,
                    p(0.25f, 0.76f).x, p(0.25f, 0.76f).y
                )
                close()
            }
            drawPath(knight, fill, style = Fill)
            drawPath(knight, strokeColor, style = stroke)

            // Eye & nostril + mane accent
            drawCircle(color = detailStroke, radius = squareSize * 0.030f, center = p(0.39f, 0.33f))
            drawLine(detailStroke, p(0.25f, 0.78f), p(0.75f, 0.78f), strokeWidth * 0.7f)
            val mane = Path().apply {
                moveTo(p(0.55f, 0.25f).x, p(0.55f, 0.25f).y)
                cubicTo(
                    p(0.67f, 0.35f).x, p(0.67f, 0.35f).y,
                    p(0.69f, 0.54f).x, p(0.69f, 0.54f).y,
                    p(0.66f, 0.72f).x, p(0.66f, 0.72f).y
                )
            }
            drawPath(mane, accent, style = thinStroke)
        }

        PieceType.BISHOP -> {
            // Pedestal
            val base = Path().apply {
                moveTo(p(0.20f, 0.87f).x, p(0.20f, 0.87f).y)
                lineTo(p(0.80f, 0.87f).x, p(0.80f, 0.87f).y)
                cubicTo(
                    p(0.73f, 0.77f).x, p(0.73f, 0.77f).y,
                    p(0.65f, 0.74f).x, p(0.65f, 0.74f).y,
                    p(0.62f, 0.67f).x, p(0.62f, 0.67f).y
                )
                lineTo(p(0.38f, 0.67f).x, p(0.38f, 0.67f).y)
                cubicTo(
                    p(0.35f, 0.74f).x, p(0.35f, 0.74f).y,
                    p(0.27f, 0.77f).x, p(0.27f, 0.77f).y,
                    p(0.20f, 0.87f).x, p(0.20f, 0.87f).y
                )
                close()
            }
            // Mitre dome
            val mitre = Path().apply {
                moveTo(p(0.37f, 0.67f).x, p(0.37f, 0.67f).y)
                cubicTo(
                    p(0.25f, 0.52f).x, p(0.25f, 0.52f).y,
                    p(0.31f, 0.28f).x, p(0.31f, 0.28f).y,
                    p(0.50f, 0.20f).x, p(0.50f, 0.20f).y
                )
                cubicTo(
                    p(0.69f, 0.28f).x, p(0.69f, 0.28f).y,
                    p(0.75f, 0.52f).x, p(0.75f, 0.52f).y,
                    p(0.63f, 0.67f).x, p(0.63f, 0.67f).y
                )
                close()
            }
            drawPath(mitre, fill, style = Fill)
            drawPath(mitre, strokeColor, style = stroke)
            drawPath(base, fill, style = Fill)
            drawPath(base, strokeColor, style = stroke)

            // Finial orb
            drawCircle(color = fill, radius = squareSize * 0.055f, center = p(0.50f, 0.145f))
            drawCircle(color = strokeColor, radius = squareSize * 0.055f, center = p(0.50f, 0.145f), style = stroke)

            // Classic mitre diagonal slit & cross mark
            drawLine(detailStroke, p(0.45f, 0.38f), p(0.55f, 0.38f), strokeWidth * 0.8f)
            drawLine(detailStroke, p(0.50f, 0.33f), p(0.50f, 0.45f), strokeWidth * 0.8f)
            drawLine(detailStroke, p(0.36f, 0.67f), p(0.64f, 0.67f), strokeWidth * 0.7f)
        }

        PieceType.QUEEN -> {
            // Five-pointed coronet
            val crown = Path().apply {
                moveTo(p(0.23f, 0.86f).x, p(0.23f, 0.86f).y)
                lineTo(p(0.77f, 0.86f).x, p(0.77f, 0.86f).y)
                lineTo(p(0.74f, 0.72f).x, p(0.74f, 0.72f).y)
                lineTo(p(0.84f, 0.30f).x, p(0.84f, 0.30f).y)
                lineTo(p(0.67f, 0.54f).x, p(0.67f, 0.54f).y)
                lineTo(p(0.67f, 0.22f).x, p(0.67f, 0.22f).y)
                lineTo(p(0.55f, 0.50f).x, p(0.55f, 0.50f).y)
                lineTo(p(0.50f, 0.18f).x, p(0.50f, 0.18f).y)
                lineTo(p(0.45f, 0.50f).x, p(0.45f, 0.50f).y)
                lineTo(p(0.33f, 0.22f).x, p(0.33f, 0.22f).y)
                lineTo(p(0.33f, 0.54f).x, p(0.33f, 0.54f).y)
                lineTo(p(0.16f, 0.30f).x, p(0.16f, 0.30f).y)
                lineTo(p(0.26f, 0.72f).x, p(0.26f, 0.72f).y)
                close()
            }
            drawPath(crown, fill, style = Fill)
            drawPath(crown, strokeColor, style = stroke)

            // Crown pearls
            val pearls = listOf(
                p(0.16f, 0.27f),
                p(0.33f, 0.19f),
                p(0.50f, 0.15f),
                p(0.67f, 0.19f),
                p(0.84f, 0.27f)
            )
            for (pt in pearls) {
                drawCircle(color = fill, radius = squareSize * 0.045f, center = pt)
                drawCircle(color = strokeColor, radius = squareSize * 0.045f, center = pt, style = thinStroke)
            }
            drawLine(detailStroke, p(0.27f, 0.73f), p(0.73f, 0.73f), strokeWidth * 0.75f)
            drawLine(detailStroke, p(0.25f, 0.80f), p(0.75f, 0.80f), strokeWidth * 0.65f)
        }

        PieceType.KING -> {
            // Cross finial
            drawLine(strokeColor, p(0.50f, 0.10f), p(0.50f, 0.27f), strokeWidth * 1.35f, cap = StrokeCap.Round)
            drawLine(strokeColor, p(0.42f, 0.165f), p(0.58f, 0.165f), strokeWidth * 1.35f, cap = StrokeCap.Round)
            if (piece.color == PieceColor.BLACK) {
                drawLine(detailStroke, p(0.50f, 0.115f), p(0.50f, 0.25f), strokeWidth * 0.55f, cap = StrokeCap.Round)
                drawLine(detailStroke, p(0.435f, 0.165f), p(0.565f, 0.165f), strokeWidth * 0.55f, cap = StrokeCap.Round)
            }

            // Twin royal lobes + central crown
            val kingBody = Path().apply {
                moveTo(p(0.23f, 0.86f).x, p(0.23f, 0.86f).y)
                lineTo(p(0.77f, 0.86f).x, p(0.77f, 0.86f).y)
                lineTo(p(0.75f, 0.71f).x, p(0.75f, 0.71f).y)
                cubicTo(
                    p(0.88f, 0.56f).x, p(0.88f, 0.56f).y,
                    p(0.83f, 0.34f).x, p(0.83f, 0.34f).y,
                    p(0.67f, 0.34f).x, p(0.67f, 0.34f).y
                )
                cubicTo(
                    p(0.61f, 0.25f).x, p(0.61f, 0.25f).y,
                    p(0.39f, 0.25f).x, p(0.39f, 0.25f).y,
                    p(0.33f, 0.34f).x, p(0.33f, 0.34f).y
                )
                cubicTo(
                    p(0.17f, 0.34f).x, p(0.17f, 0.34f).y,
                    p(0.12f, 0.56f).x, p(0.12f, 0.56f).y,
                    p(0.25f, 0.71f).x, p(0.25f, 0.71f).y
                )
                close()
            }
            drawPath(kingBody, fill, style = Fill)
            drawPath(kingBody, strokeColor, style = stroke)

            // Central vertical rib & crown band
            drawLine(detailStroke, p(0.50f, 0.31f), p(0.50f, 0.68f), strokeWidth * 0.72f)
            drawLine(detailStroke, p(0.26f, 0.72f), p(0.74f, 0.72f), strokeWidth * 0.75f)
            drawLine(detailStroke, p(0.25f, 0.79f), p(0.75f, 0.79f), strokeWidth * 0.65f)
        }
    }
}
