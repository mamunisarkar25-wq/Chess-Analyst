package com.example.chess.analysis

import androidx.compose.ui.graphics.Color
import com.example.chess.core.ParsedMoveNode
import com.example.chess.core.ParsedPgnGame
import com.example.chess.engine.EvalScore
import com.example.chess.engine.MaiaPrediction
import com.example.chess.engine.PvLine
import com.example.ui.theme.ClassBest
import com.example.ui.theme.ClassBlunder
import com.example.ui.theme.ClassBrilliant
import com.example.ui.theme.ClassExcellent
import com.example.ui.theme.ClassGood
import com.example.ui.theme.ClassGreat
import com.example.ui.theme.ClassInaccurate
import com.example.ui.theme.ClassMiss
import com.example.ui.theme.ClassMistake

enum class MoveClassification(
    val label: String,
    val badgeSymbol: String,
    val nag: String,
    val badgeColor: Color,
    val sortOrder: Int
) {
    BRILLIANT("Brilliant", "!!", "$3", ClassBrilliant, 0),
    GREAT("Great", "!", "$1", ClassGreat, 1),
    BEST("Best", "★", "", ClassBest, 2),
    EXCELLENT("Excellent", "✓", "", ClassExcellent, 3),
    GOOD("Good", "○", "", ClassGood, 4),
    INACCURATE("Inaccurate", "?!", "$6", ClassInaccurate, 5),
    MISS("Miss", "✕", "$2", ClassMiss, 6),
    MISTAKE("Mistake", "?", "$2", ClassMistake, 7),
    BLUNDER("Blunder", "??", "$4", ClassBlunder, 8)
}

enum class GamePhase(val displayTitle: String) {
    OPENING("OPENING"),
    MIDDLEGAME("MIDDLEGAME"),
    ENDGAME("ENDGAME")
}

data class AnalyzedMove(
    val node: ParsedMoveNode,
    val phase: GamePhase,
    val evalBefore: EvalScore,
    val evalAfter: EvalScore,
    val centipawnLoss: Int,
    val winProbLoss: Double,
    val moveAccuracy: Double,
    val classification: MoveClassification,
    val bestAlternativeUci: String?,
    val bestAlternativeSan: String?,
    val bestLineSan: List<String>,
    val pvLines: List<PvLine>,
    val explanation: String,
    val maiaPrediction: MaiaPrediction? = null
) {
    val evalAfterDisplay: String get() = evalAfter.formatDisplay()
}

data class PlayerPhaseStats(
    val moveCount: Int,
    val accuracy: Double,
    val avgCentipawnLoss: Double,
    val bestOrBetterCount: Int,
    val mistakesAndBlundersCount: Int,
    val classificationCounts: Map<MoveClassification, Int>
)

data class PhaseAnalysisSummary(
    val phase: GamePhase,
    val reached: Boolean,
    val startPly: Int? = null,
    val endPly: Int? = null,
    val whiteStats: PlayerPhaseStats? = null,
    val blackStats: PlayerPhaseStats? = null
)

data class GameReviewReport(
    val id: String,
    val game: ParsedPgnGame,
    val analyzedAtMillis: Long,
    val engineName: String,
    val searchDepth: Int,
    val initialEval: EvalScore,
    val moves: List<AnalyzedMove>,
    val whiteAccuracy: Double,
    val blackAccuracy: Double,
    val whiteAcpl: Double,
    val blackAcpl: Double,
    val whiteEstimatedElo: Int,
    val blackEstimatedElo: Int,
    val whiteClassificationCounts: Map<MoveClassification, Int>,
    val blackClassificationCounts: Map<MoveClassification, Int>,
    val phaseSummaries: List<PhaseAnalysisSummary>,
    val sourceLabel: String // e.g., "PGN Import", "Chess.com"
)

data class ReviewProgress(
    val currentPly: Int,
    val totalPlies: Int,
    val statusText: String
) {
    val fraction: Float get() = if (totalPlies <= 0) 0f else (currentPly.toFloat() / totalPlies.toFloat()).coerceIn(0f, 1f)
}
