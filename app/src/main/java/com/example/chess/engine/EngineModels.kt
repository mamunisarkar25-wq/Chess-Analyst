package com.example.chess.engine

import com.example.chess.core.PieceColor
import kotlin.math.abs

/**
 * Standardized evaluation score from White's perspective.
 * If [mateIn] != null, positive = White mates in N, negative = Black mates in N.
 * Otherwise [centipawns] is White's advantage in centipawns.
 */
data class EvalScore(
    val centipawns: Int = 0,
    val mateIn: Int? = null
) {
    /** Converts score to an effective White centipawn value for graphing and win-probability math. */
    fun toEffectiveWhiteCp(): Int {
        return when {
            mateIn != null && mateIn > 0 -> 10000 - (mateIn * 10).coerceAtMost(900)
            mateIn != null && mateIn < 0 -> -10000 + (abs(mateIn) * 10).coerceAtMost(900)
            mateIn == 0 -> 0
            else -> centipawns
        }
    }

    /** Perspective-adjusted effective centipawns for [side]. */
    fun toPerspectiveCp(side: PieceColor): Int {
        val whiteCp = toEffectiveWhiteCp()
        return if (side == PieceColor.WHITE) whiteCp else -whiteCp
    }

    fun formatDisplay(): String {
        return when {
            mateIn != null && mateIn > 0 -> "+M$mateIn"
            mateIn != null && mateIn < 0 -> "-M${abs(mateIn)}"
            mateIn == 0 -> "0.0"
            else -> {
                val pawns = centipawns / 100.0
                if (pawns > 0.04) "+${"%.1f".format(pawns)}"
                else if (pawns < -0.04) "%.1f".format(pawns)
                else "0.0"
            }
        }
    }

    companion object {
        fun fromSideToMove(cpFromMover: Int, mateFromMover: Int?, sideToMove: PieceColor): EvalScore {
            val sign = if (sideToMove == PieceColor.WHITE) 1 else -1
            return EvalScore(
                centipawns = cpFromMover * sign,
                mateIn = mateFromMover?.let { it * sign }
            )
        }
    }
}

data class PvLine(
    val multiPvIndex: Int,
    val score: EvalScore,
    val uciMoves: List<String>,
    val sanMoves: List<String> = emptyList(),
    val depth: Int = 0
)

data class EngineEvaluation(
    val fen: String,
    val score: EvalScore,
    val bestMoveUci: String?,
    val bestMoveSan: String?,
    val secondBestScore: EvalScore? = null,
    val pvLines: List<PvLine> = emptyList(),
    val depth: Int = 0,
    val nodes: Long = 0,
    val engineName: String = "Tactical Engine"
)

data class MaiaMoveCandidate(
    val uci: String,
    val san: String,
    val probability: Float // 0f..1f
)

data class MaiaPrediction(
    val fen: String,
    val targetElo: Int,
    val modelName: String,
    val candidates: List<MaiaMoveCandidate>,
    val winProbability: Float? = null,
    val drawProbability: Float? = null,
    val lossProbability: Float? = null
)

/**
 * Real Maia-3 ONNX model variants hosted on Hugging Face (bqrio/maia3-onnx).
 * 79M is strictly opt-in and never loaded by default to protect mid-range Android devices.
 */
enum class MaiaModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeMb: Int,
    val ramRequirementLabel: String,
    val recommendation: String,
    val isHeavy: Boolean = false
) {
    MAIA3_3M(
        id = "maia3_3m",
        displayName = "Maia-3 3M (FP16)",
        fileName = "maia3-3m-ablation.fp16.onnx",
        downloadUrl = "https://huggingface.co/bqrio/maia3-onnx/resolve/main/maia3-3m-ablation.fp16.onnx",
        sizeMb = 7,
        ramRequirementLabel = "~45 MB RAM",
        recommendation = "Fastest • Ideal for entry & mid-range phones"
    ),
    MAIA3_5M(
        id = "maia3_5m",
        displayName = "Maia-3 5M (FP16)",
        fileName = "maia3-5m.fp16.onnx",
        downloadUrl = "https://huggingface.co/bqrio/maia3-onnx/resolve/main/maia3-5m.fp16.onnx",
        sizeMb = 11,
        ramRequirementLabel = "~65 MB RAM",
        recommendation = "Recommended • Great balance of speed & human accuracy"
    ),
    MAIA3_23M(
        id = "maia3_23m",
        displayName = "Maia-3 23M (FP16)",
        fileName = "maia3-23m.fp16.onnx",
        downloadUrl = "https://huggingface.co/bqrio/maia3-onnx/resolve/main/maia3-23m.fp16.onnx",
        sizeMb = 46,
        ramRequirementLabel = "~180 MB RAM",
        recommendation = "High human-prediction accuracy • Modern mid/high-end devices"
    ),
    MAIA3_79M(
        id = "maia3_79m",
        displayName = "Maia-3 79M (FP16)",
        fileName = "maia3-79m.fp16.onnx",
        downloadUrl = "https://huggingface.co/bqrio/maia3-onnx/resolve/main/maia3-79m.fp16.onnx",
        sizeMb = 158,
        ramRequirementLabel = "~480 MB RAM",
        recommendation = "Opt-in only • Flagship devices (not loaded by default)",
        isHeavy = true
    );

    companion object {
        fun fromId(id: String): MaiaModelSpec = entries.firstOrNull { it.id == id } ?: MAIA3_5M
    }
}
