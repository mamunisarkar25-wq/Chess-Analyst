package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.engine.EvalScore
import com.example.ui.theme.EvalBarBlack
import com.example.ui.theme.EvalBarWhite
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.ObsidianBorder
import kotlin.math.tanh

/**
 * Animated vertical evaluation bar displayed alongside the chessboard.
 */
@Composable
fun EvaluationBar(
    score: EvalScore,
    flipped: Boolean,
    modifier: Modifier = Modifier
) {
    val whiteShareTarget = when {
        score.mateIn != null && score.mateIn > 0 -> 1f
        score.mateIn != null && score.mateIn < 0 -> 0f
        else -> {
            // Smooth tanh compression: +400 cp -> ~85%, +800 cp -> ~96%
            (0.5 + 0.48 * tanh(score.centipawns / 420.0)).toFloat().coerceIn(0.03f, 0.97f)
        }
    }
    val whiteShare by animateFloatAsState(
        targetValue = whiteShareTarget,
        animationSpec = tween(durationMillis = 320),
        label = "eval_bar_fill"
    )

    val whiteAdvantage = (score.mateIn != null && score.mateIn > 0) ||
        (score.mateIn == null && score.centipawns >= 0)

    val label = score.formatDisplay().replace("+", "")

    Box(
        modifier = modifier
            .width(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(EvalBarBlack)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(6.dp))
    ) {
        // White fill aligns to bottom normally, or top when board is flipped
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(whiteShare)
                .align(if (flipped) Alignment.TopCenter else Alignment.BottomCenter)
                .background(EvalBarWhite)
        )

        // Score readout pinned to winning side's end
        val textOnTop = (whiteAdvantage && flipped) || (!whiteAdvantage && !flipped)
        val textColor = if (whiteAdvantage) Color(0xFF1A1917) else Color(0xFFF3F1EC)

        Text(
            text = label,
            color = textColor,
            fontFamily = JetBrainsMonoFont,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .align(if (textOnTop) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(vertical = 4.dp, horizontal = 1.dp)
        )
    }
}
