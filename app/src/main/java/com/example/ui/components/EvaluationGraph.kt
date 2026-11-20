package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.chess.analysis.AnalyzedMove
import com.example.chess.analysis.MoveClassification
import com.example.chess.engine.EvalScore
import com.example.ui.theme.EvalBarBlack
import com.example.ui.theme.EvalBarWhite
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.TournamentEmerald
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Interactive evaluation advantage graph across all plies.
 * Tapping anywhere along the chart jumps directly to that move in the Game Review.
 */
@Composable
fun EvaluationGraph(
    initialEval: EvalScore,
    moves: List<AnalyzedMove>,
    currentPly: Int,
    onSelectPly: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scores = buildList {
        add(initialEval)
        moves.forEach { add(it.evalAfter) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(EvalBarBlack)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
            .testTag("evaluation_graph")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(scores.size) {
                    detectTapGestures { offset ->
                        if (scores.size <= 1) return@detectTapGestures
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val ply = (fraction * (scores.size - 1)).roundToInt().coerceIn(0, scores.size - 1)
                        onSelectPly(ply)
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val midY = h / 2f

            // Equal baseline
            drawLine(
                color = Color(0x44FFFFFF),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 1.dp.toPx()
            )

            if (scores.size < 2) return@Canvas

            val stepX = w / (scores.size - 1).toFloat()

            fun scoreToY(score: EvalScore): Float {
                val norm = when {
                    score.mateIn != null && score.mateIn > 0 -> 1.0
                    score.mateIn != null && score.mateIn < 0 -> -1.0
                    else -> tanh(score.centipawns / 450.0)
                }
                // +1 -> top (4% margin), -1 -> bottom (96%)
                return (midY - (norm * (h * 0.45f)).toFloat()).coerceIn(h * 0.04f, h * 0.96f)
            }

            // Filled white-advantage area from bottom to curve
            val fillPath = Path().apply {
                moveTo(0f, h)
                scores.forEachIndexed { i, sc ->
                    lineTo(i * stepX, scoreToY(sc))
                }
                lineTo(w, h)
                close()
            }
            drawPath(fillPath, color = EvalBarWhite.copy(alpha = 0.88f))

            // Crisp top stroke
            val strokePath = Path().apply {
                scores.forEachIndexed { i, sc ->
                    val x = i * stepX
                    val y = scoreToY(sc)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(
                path = strokePath,
                color = Color(0xFF9C988F),
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
            )

            // Highlight notable moves (Brilliant, Great, Mistake, Blunder, Miss) as dots
            moves.forEachIndexed { idx, am ->
                val notable = am.classification == MoveClassification.BRILLIANT ||
                    am.classification == MoveClassification.GREAT ||
                    am.classification == MoveClassification.BLUNDER ||
                    am.classification == MoveClassification.MISTAKE ||
                    am.classification == MoveClassification.MISS
                if (notable) {
                    val x = (idx + 1) * stepX
                    val y = scoreToY(am.evalAfter)
                    drawCircle(color = Color(0xFF161512), radius = 4.2.dp.toPx(), center = Offset(x, y))
                    drawCircle(color = am.classification.badgeColor, radius = 3.0.dp.toPx(), center = Offset(x, y))
                }
            }

            // Active ply scrubber vertical bar
            val clampedPly = currentPly.coerceIn(0, scores.size - 1)
            val curX = clampedPly * stepX
            val curY = scoreToY(scores[clampedPly])
            drawLine(
                color = TournamentEmerald,
                start = Offset(curX, 0f),
                end = Offset(curX, h),
                strokeWidth = 2.dp.toPx()
            )
            drawCircle(color = Color(0xFF161512), radius = 5.5.dp.toPx(), center = Offset(curX, curY))
            drawCircle(color = TournamentEmerald, radius = 4.dp.toPx(), center = Offset(curX, curY))
        }
    }
}
