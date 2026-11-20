package com.example.chess.analysis

import com.example.chess.core.ChessBoardState
import com.example.chess.core.FenValidationResult
import com.example.chess.core.ParsedMoveNode
import com.example.chess.core.ParsedPgnGame
import com.example.chess.core.PieceColor
import com.example.chess.core.PieceType
import com.example.chess.engine.EngineEvaluation
import com.example.chess.engine.EvalScore
import com.example.chess.engine.StockfishUciEngine
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Open, documented Chess Game Review engine.
 *
 * Explicitly does NOT reproduce Chess.com's proprietary formula. Instead uses a transparent
 * logistic win-probability curve, centipawn-loss thresholds, material-sacrifice detection,
 * volatility-weighted accuracy, and deterministic material-based game phase segmentation.
 */
object GameAnalyzer {

    const val METHODOLOGY_SUMMARY =
        "Chess Analyst uses an open, documented classification algorithm (not Chess.com's proprietary formula):\n\n" +
            "• Win Probability: Win%(cp) = 50 + 50 × (2 / (1 + e^(-0.00368208 × cp)) - 1)\n" +
            "• Brilliant (!!): Tactical sacrifice that stays within 1.5% win-probability of best in a non-trivial position with >=120cp gap over 2nd best.\n" +
            "• Great (!): Only good move in a sharp/critical position (>=150cp gap over 2nd best).\n" +
            "• Best / Excellent / Good / Inaccurate / Mistake / Blunder: Graded by Win% loss (<=1%, 2.5%, 5.5%, 10%, 20%, >20%) and centipawn loss.\n" +
            "• Miss: Missed a winning tactic (>=+2.5 or forced mate) while remaining near-equal.\n" +
            "• Accuracy: Volatility-weighted blend of 103.17 × e^(-0.04354 × ΔWin%) - 3.17.\n" +
            "• Phases: Opening -> Middlegame (material < 50, queens off, or move > 15) -> Endgame (total non-pawn material <= 26)."

    /**
     * Converts White-perspective centipawns to White's expected winning percentage (0..100).
     */
    fun winProbabilityPercent(score: EvalScore, perspective: PieceColor): Double {
        val mate = score.mateIn
        if (mate != null && mate != 0) {
            val isOurMate = (perspective == PieceColor.WHITE && mate > 0) ||
                (perspective == PieceColor.BLACK && mate < 0)
            return if (isOurMate) 100.0 else 0.0
        }
        val cp = score.toPerspectiveCp(perspective).coerceIn(-2000, 2000).toDouble()
        return 50.0 + 50.0 * (2.0 / (1.0 + exp(-0.00368208 * cp)) - 1.0)
    }

    /**
     * Converts Win-Probability loss (0..100) to per-move accuracy (0..100).
     */
    fun moveAccuracyFromWinLoss(winProbLoss: Double): Double {
        if (winProbLoss <= 0.05) return 100.0
        val raw = 103.1668 * exp(-0.04354 * winProbLoss) - 3.1669
        return raw.coerceIn(0.0, 100.0)
    }

    /**
     * Calculates volatility-weighted player accuracy from individual move accuracies and eval swings.
     */
    fun calculatePlayerAccuracy(moves: List<AnalyzedMove>): Double {
        if (moves.isEmpty()) return 0.0
        var weightedSum = 0.0
        var totalWeight = 0.0
        var harmonicDenom = 0.0

        for (m in moves) {
            val weight = (1.0 + (m.winProbLoss / 25.0)).coerceIn(0.5, 4.0)
            weightedSum += m.moveAccuracy * weight
            totalWeight += weight
            harmonicDenom += 1.0 / max(m.moveAccuracy, 1.0)
        }
        val weightedMean = if (totalWeight > 0) weightedSum / totalWeight else 0.0
        val harmonicMean = moves.size / harmonicDenom
        return ((weightedMean * 0.72) + (harmonicMean * 0.28)).coerceIn(0.0, 100.0)
    }

    /**
     * Estimates performance rating from accuracy, ACPL, and opponent Elo (if present).
     */
    fun estimatePerformanceRating(
        accuracy: Double,
        acpl: Double,
        opponentElo: Int?,
        gameOutcomeScore: Double? // 1.0 win, 0.5 draw, 0.0 loss
    ): Int {
        // Base review-rating curve from accuracy & ACPL (maps ~50% -> 800, 75% -> 1450, 88% -> 1950, 96% -> 2450)
        val accRating = (400.0 + (accuracy * 21.5) - (acpl.coerceAtMost(200.0) * 1.4)).coerceIn(400.0, 2850.0)
        if (opponentElo != null && opponentElo in 300..3200) {
            val resultBonus = when (gameOutcomeScore) {
                1.0 -> 200.0
                0.5 -> 0.0
                0.0 -> -200.0
                else -> 0.0
            }
            val perfFromOpponent = (opponentElo + resultBonus).coerceIn(400.0, 3000.0)
            return ((accRating * 0.65) + (perfFromOpponent * 0.35)).roundToInt().coerceIn(400, 2900)
        }
        return accRating.roundToInt()
    }

    /**
     * Determines the phase sequence (OPENING -> MIDDLEGAME -> ENDGAME) monotonically across plies.
     */
    fun assignGamePhases(initialFen: String, moves: List<ParsedMoveNode>): List<GamePhase> {
        if (moves.isEmpty()) return emptyList()
        var currentPhase = GamePhase.OPENING
        val result = ArrayList<GamePhase>(moves.size)

        // If game starts from a custom FEN already in endgame/middlegame, reflect actual material
        val initBoard = (ChessBoardState.fromFen(initialFen) as? FenValidationResult.Valid)?.state
        if (initBoard != null) {
            val initMat = initBoard.totalNonPawnMaterialPoints()
            if (initMat <= 26) currentPhase = GamePhase.ENDGAME
            else if (initMat < 50 || initBoard.fullMoveNumber > 15) currentPhase = GamePhase.MIDDLEGAME
        }

        for (node in moves) {
            val boardAfter = (ChessBoardState.fromFen(node.fenAfter) as? FenValidationResult.Valid)?.state
            if (boardAfter != null) {
                val totalNonPawn = boardAfter.totalNonPawnMaterialPoints()
                val whiteNonPawn = boardAfter.sideNonPawnMaterialPoints(PieceColor.WHITE)
                val blackNonPawn = boardAfter.sideNonPawnMaterialPoints(PieceColor.BLACK)
                val queensOnBoard = boardAfter.squares.count { it?.type == PieceType.QUEEN }

                if (currentPhase == GamePhase.OPENING) {
                    val pastOpeningMove = node.moveNumber >= 12 && (
                        countDevelopedMinors(boardAfter) >= 5 || node.moveNumber > 15
                    )
                    if (totalNonPawn < 50 || queensOnBoard == 0 || pastOpeningMove) {
                        currentPhase = GamePhase.MIDDLEGAME
                    }
                }
                if (currentPhase != GamePhase.ENDGAME) {
                    if (totalNonPawn <= 26 || (whiteNonPawn <= 13 && blackNonPawn <= 13)) {
                        currentPhase = GamePhase.ENDGAME
                    }
                }
            }
            result.add(currentPhase)
        }
        return result
    }

    private fun countDevelopedMinors(board: ChessBoardState): Int {
        // Count knights & bishops not on their starting rank (rank 0 for White, rank 7 for Black)
        var developed = 0
        for (sq in 0..63) {
            val p = board.squares[sq] ?: continue
            if (p.type != PieceType.KNIGHT && p.type != PieceType.BISHOP) continue
            val rank = sq ushr 3
            if ((p.color == PieceColor.WHITE && rank > 0) || (p.color == PieceColor.BLACK && rank < 7)) {
                developed++
            }
        }
        return developed
    }

    /**
     * Classifies a single move using the documented Win-Probability + CPL + sacrifice rules.
     */
    fun classifyMove(
        node: ParsedMoveNode,
        boardBefore: ChessBoardState,
        boardAfter: ChessBoardState,
        evalBefore: EngineEvaluation,
        evalAfterScore: EvalScore
    ): MoveClassification {
        val mover = node.side
        val bestUci = evalBefore.bestMoveUci
        val isTopEngineMove = bestUci != null && bestUci == node.uci

        val winBefore = winProbabilityPercent(evalBefore.score, mover)
        val winAfter = winProbabilityPercent(evalAfterScore, mover)
        val winLoss = max(0.0, winBefore - winAfter)

        val bestCp = evalBefore.score.toPerspectiveCp(mover)
        val afterCp = evalAfterScore.toPerspectiveCp(mover)
        val cpl = max(0, bestCp - afterCp)

        val secondBestCp = evalBefore.secondBestScore?.toPerspectiveCp(mover)
        val gapToSecond = if (secondBestCp != null) bestCp - secondBestCp else 0

        // Check for missed/allowed forced mate
        val missedMate = (evalBefore.score.mateIn != null &&
            ((mover == PieceColor.WHITE && evalBefore.score.mateIn > 0) ||
                (mover == PieceColor.BLACK && evalBefore.score.mateIn < 0))) &&
            (evalAfterScore.mateIn == null ||
                (mover == PieceColor.WHITE && evalAfterScore.mateIn <= 0) ||
                (mover == PieceColor.BLACK && evalAfterScore.mateIn >= 0))

        val allowedMate = (evalAfterScore.mateIn != null &&
            ((mover == PieceColor.WHITE && evalAfterScore.mateIn < 0) ||
                (mover == PieceColor.BLACK && evalAfterScore.mateIn > 0))) &&
            !(evalBefore.score.mateIn != null &&
                ((mover == PieceColor.WHITE && evalBefore.score.mateIn < 0) ||
                    (mover == PieceColor.BLACK && evalBefore.score.mateIn > 0)))

        if (allowedMate && winLoss > 12.0) return MoveClassification.BLUNDER

        // Sacrifice detection for Brilliant: moved non-pawn piece onto an opponent-attacked square
        // without equal/higher capture value, or material dropped while eval stayed strong
        val movedPiece = boardBefore.pieceAt(node.move.from)
        val capturedVal = node.move.capturedPiece?.type?.valueCp ?: 0
        val movedVal = movedPiece?.type?.valueCp ?: 0
        val offeredPieceEnPrise = movedPiece != null &&
            movedPiece.type != PieceType.PAWN &&
            movedPiece.type != PieceType.KING &&
            boardAfter.isSquareAttackedBy(node.move.to.index, mover.opposite()) &&
            (movedVal - capturedVal >= 150)

        if (offeredPieceEnPrise &&
            (isTopEngineMove || winLoss <= 1.5) &&
            bestCp in -150..550 &&
            gapToSecond >= 120
        ) {
            return MoveClassification.BRILLIANT
        }

        // Great: critical only move (large gap over second best) that holds or seizes initiative
        if ((isTopEngineMove || winLoss <= 2.0) && gapToSecond >= 150 && abs(bestCp) <= 650) {
            return MoveClassification.GREAT
        }

        if (isTopEngineMove || (winLoss <= 1.0 && cpl <= 10)) {
            return MoveClassification.BEST
        }
        if (winLoss <= 2.5 || cpl <= 25) {
            return MoveClassification.EXCELLENT
        }
        if (winLoss <= 5.5 || cpl <= 55) {
            return MoveClassification.GOOD
        }

        // Miss: missed a winning advantage (+250cp or forced mate) while keeping position playable
        if ((bestCp >= 250 || missedMate) && afterCp >= -50 && winLoss >= 10.0) {
            return MoveClassification.MISS
        }

        if (winLoss <= 10.0 || cpl <= 110) {
            return MoveClassification.INACCURATE
        }
        if (winLoss <= 20.0 || cpl <= 220) {
            return MoveClassification.MISTAKE
        }
        return MoveClassification.BLUNDER
    }

    /**
     * Generates a clear, chess-grounded tactical explanation for a classified move.
     */
    fun generateMoveExplanation(
        node: ParsedMoveNode,
        classification: MoveClassification,
        evalBefore: EvalScore,
        evalAfter: EvalScore,
        cpl: Int,
        bestSan: String?,
        bestLineSan: List<String>,
        boardAfter: ChessBoardState
    ): String {
        val sideName = if (node.side == PieceColor.WHITE) "White" else "Black"
        val pvPreview = bestLineSan.take(4).joinToString(" ")
        val mateSuffix = if (node.san.endsWith("#")) " Delivers checkmate!" else ""

        return when (classification) {
            MoveClassification.BRILLIANT ->
                "${node.san} is a brilliant tactical sacrifice! $sideName offers material to keep the best evaluation (${evalAfter.formatDisplay()}) with a sharp follow-up${if (pvPreview.isNotBlank()) " ($pvPreview)" else ""}."
            MoveClassification.GREAT ->
                "${node.san} is the critical only move in a sharp position, maintaining ${evalAfter.formatDisplay()} where alternatives drop sharply."
            MoveClassification.BEST ->
                if (mateSuffix.isNotEmpty()) "${node.san} finishes the game with checkmate."
                else "${node.san} matches the engine's strongest continuation (${evalAfter.formatDisplay()})."
            MoveClassification.EXCELLENT ->
                "${node.san} is a strong, accurate move (${evalAfter.formatDisplay()})." +
                    if (bestSan != null && bestSan != node.san) " Engine slightly preferred $bestSan." else ""
            MoveClassification.GOOD ->
                "${node.san} keeps a reasonable position (${evalAfter.formatDisplay()})." +
                    if (bestSan != null && bestSan != node.san) " $bestSan offered slightly better activity." else ""
            MoveClassification.INACCURATE ->
                "${node.san} is a slight inaccuracy (losing ~${"%.1f".format(cpl / 100.0)} pawns, eval ${evalAfter.formatDisplay()})." +
                    if (bestSan != null) " Better was $bestSan${if (pvPreview.isNotBlank()) " ($pvPreview)" else ""}." else ""
            MoveClassification.MISS ->
                "${node.san} missed a decisive winning opportunity (eval went from ${evalBefore.formatDisplay()} to ${evalAfter.formatDisplay()})." +
                    if (bestSan != null) " $bestSan would have seized a clear advantage${if (pvPreview.isNotBlank()) " via $pvPreview" else ""}." else ""
            MoveClassification.MISTAKE ->
                "${node.san} is a mistake, swinging the evaluation from ${evalBefore.formatDisplay()} to ${evalAfter.formatDisplay()}." +
                    if (bestSan != null) " Stronger was $bestSan${if (pvPreview.isNotBlank()) " ($pvPreview)" else ""}." else ""
            MoveClassification.BLUNDER -> {
                val mateNote = if (evalAfter.mateIn != null) " This allows a forced mating sequence (${evalAfter.formatDisplay()})." else ""
                val hangingNote = if (boardAfter.isInCheck(node.side.opposite())) "" else " Evaluation dropped by ${"%.1f".format(cpl / 100.0)} pawns."
                "${node.san} is a critical blunder!$mateNote$hangingNote" +
                    if (bestSan != null) " The best continuation was $bestSan${if (pvPreview.isNotBlank()) " ($pvPreview)" else ""}." else ""
            }
        }
    }

    /**
     * Runs complete move-by-move local analysis for a parsed game using [engine].
     * Supports cooperative cancellation and streams progress callbacks after every ply.
     */
    suspend fun analyzeGame(
        game: ParsedPgnGame,
        engine: StockfishUciEngine,
        searchDepth: Int = 4,
        timePerMoveMs: Long = 350L,
        sourceLabel: String = "PGN Import",
        onProgress: (ReviewProgress) -> Unit = {}
    ): GameReviewReport {
        val moves = game.moves
        val totalPositions = moves.size + 1
        val phases = assignGamePhases(game.initialFen, moves)

        // Evaluate initial position and every position after each ply.
        // We reuse each position's evaluation as both `after` for ply i and `before` for ply i+1!
        val evaluations = ArrayList<EngineEvaluation>(totalPositions)
        onProgress(ReviewProgress(0, moves.size, "Evaluating starting position..."))
        val initEval = engine.evaluatePosition(
            fen = game.initialFen,
            depth = searchDepth,
            timeLimitMs = timePerMoveMs,
            multiPv = 2
        )
        evaluations.add(initEval)

        for (i in moves.indices) {
            coroutineContext.ensureActive()
            val node = moves[i]
            onProgress(
                ReviewProgress(
                    currentPly = i + 1,
                    totalPlies = moves.size,
                    statusText = "Analyzing move ${node.moveNumber}${if (node.side == PieceColor.WHITE) "." else "..."} ${node.san}"
                )
            )
            val evalAfter = engine.evaluatePosition(
                fen = node.fenAfter,
                depth = searchDepth,
                timeLimitMs = timePerMoveMs,
                multiPv = 2
            )
            evaluations.add(evalAfter)
        }

        val analyzedMoves = ArrayList<AnalyzedMove>(moves.size)
        for (i in moves.indices) {
            val node = moves[i]
            val evalBefore = evaluations[i]
            val evalAfter = evaluations[i + 1]
            val boardBefore = (ChessBoardState.fromFen(node.fenBefore) as FenValidationResult.Valid).state
            val boardAfter = (ChessBoardState.fromFen(node.fenAfter) as FenValidationResult.Valid).state

            val winBefore = winProbabilityPercent(evalBefore.score, node.side)
            val winAfter = winProbabilityPercent(evalAfter.score, node.side)
            val winLoss = max(0.0, winBefore - winAfter)
            val cpl = max(0, evalBefore.score.toPerspectiveCp(node.side) - evalAfter.score.toPerspectiveCp(node.side))
                .coerceAtMost(1500)
            val accuracy = moveAccuracyFromWinLoss(winLoss)
            val classification = classifyMove(node, boardBefore, boardAfter, evalBefore, evalAfter.score)
            val bestLineSan = evalBefore.pvLines.firstOrNull()?.sanMoves.orEmpty()
            val explanation = generateMoveExplanation(
                node = node,
                classification = classification,
                evalBefore = evalBefore.score,
                evalAfter = evalAfter.score,
                cpl = cpl,
                bestSan = evalBefore.bestMoveSan,
                bestLineSan = bestLineSan,
                boardAfter = boardAfter
            )

            analyzedMoves.add(
                AnalyzedMove(
                    node = node,
                    phase = phases[i],
                    evalBefore = evalBefore.score,
                    evalAfter = evalAfter.score,
                    centipawnLoss = cpl,
                    winProbLoss = winLoss,
                    moveAccuracy = accuracy,
                    classification = classification,
                    bestAlternativeUci = evalBefore.bestMoveUci,
                    bestAlternativeSan = evalBefore.bestMoveSan,
                    bestLineSan = bestLineSan,
                    pvLines = evalBefore.pvLines,
                    explanation = explanation
                )
            )
        }

        val whiteMoves = analyzedMoves.filter { it.node.side == PieceColor.WHITE }
        val blackMoves = analyzedMoves.filter { it.node.side == PieceColor.BLACK }

        val whiteAcc = calculatePlayerAccuracy(whiteMoves)
        val blackAcc = calculatePlayerAccuracy(blackMoves)
        val whiteAcpl = if (whiteMoves.isEmpty()) 0.0 else whiteMoves.map { it.centipawnLoss }.average()
        val blackAcpl = if (blackMoves.isEmpty()) 0.0 else blackMoves.map { it.centipawnLoss }.average()

        val (whiteScore, blackScore) = when (game.result) {
            "1-0" -> 1.0 to 0.0
            "0-1" -> 0.0 to 1.0
            "1/2-1/2" -> 0.5 to 0.5
            else -> null to null
        }

        val whiteEstElo = estimatePerformanceRating(whiteAcc, whiteAcpl, game.blackElo, whiteScore)
        val blackEstElo = estimatePerformanceRating(blackAcc, blackAcpl, game.whiteElo, blackScore)

        val phaseSummaries = buildPhaseSummaries(analyzedMoves)

        return GameReviewReport(
            id = "review_${game.id}_${System.currentTimeMillis()}",
            game = game,
            analyzedAtMillis = System.currentTimeMillis(),
            engineName = evaluations.lastOrNull()?.engineName ?: engine.activeEngineLabel,
            searchDepth = searchDepth,
            initialEval = initEval.score,
            moves = analyzedMoves,
            whiteAccuracy = whiteAcc,
            blackAccuracy = blackAcc,
            whiteAcpl = whiteAcpl,
            blackAcpl = blackAcpl,
            whiteEstimatedElo = whiteEstElo,
            blackEstimatedElo = blackEstElo,
            whiteClassificationCounts = countClassifications(whiteMoves),
            blackClassificationCounts = countClassifications(blackMoves),
            phaseSummaries = phaseSummaries,
            sourceLabel = sourceLabel
        )
    }

    fun buildPhaseSummaries(analyzedMoves: List<AnalyzedMove>): List<PhaseAnalysisSummary> {
        return GamePhase.entries.map { phase ->
            val phaseMoves = analyzedMoves.filter { it.phase == phase }
            if (phaseMoves.isEmpty()) {
                PhaseAnalysisSummary(phase = phase, reached = false)
            } else {
                val whitePhase = phaseMoves.filter { it.node.side == PieceColor.WHITE }
                val blackPhase = phaseMoves.filter { it.node.side == PieceColor.BLACK }
                PhaseAnalysisSummary(
                    phase = phase,
                    reached = true,
                    startPly = phaseMoves.first().node.plyIndex,
                    endPly = phaseMoves.last().node.plyIndex,
                    whiteStats = buildPlayerPhaseStats(whitePhase),
                    blackStats = buildPlayerPhaseStats(blackPhase)
                )
            }
        }
    }

    private fun buildPlayerPhaseStats(moves: List<AnalyzedMove>): PlayerPhaseStats? {
        if (moves.isEmpty()) return null
        val counts = countClassifications(moves)
        val bestOrBetter = (counts[MoveClassification.BRILLIANT] ?: 0) +
            (counts[MoveClassification.GREAT] ?: 0) +
            (counts[MoveClassification.BEST] ?: 0)
        val errors = (counts[MoveClassification.MISTAKE] ?: 0) +
            (counts[MoveClassification.BLUNDER] ?: 0) +
            (counts[MoveClassification.MISS] ?: 0)
        return PlayerPhaseStats(
            moveCount = moves.size,
            accuracy = calculatePlayerAccuracy(moves),
            avgCentipawnLoss = moves.map { it.centipawnLoss }.average(),
            bestOrBetterCount = bestOrBetter,
            mistakesAndBlundersCount = errors,
            classificationCounts = counts
        )
    }

    private fun countClassifications(moves: List<AnalyzedMove>): Map<MoveClassification, Int> {
        val map = linkedMapOf<MoveClassification, Int>()
        for (c in MoveClassification.entries) map[c] = 0
        for (m in moves) map[m.classification] = (map[m.classification] ?: 0) + 1
        return map
    }
}
