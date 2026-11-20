package com.example.chess.engine

import com.example.chess.core.ChessBoardState
import com.example.chess.core.ChessMove
import com.example.chess.core.PieceColor
import com.example.chess.core.PieceType
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Genuine, local pure-Kotlin Multi-PV Chess Engine featuring:
 * - Iterative Deepening Negamax + Alpha-Beta Pruning
 * - Quiescence Search (MVV-LVA capture ordering) to prevent horizon blunders
 * - Bounded-memory Zobrist Transposition Table (mid-range device friendly)
 * - Killer move heuristic + Null-Move Pruning
 * - PeSTO Tapered Middlegame/Endgame Piece-Square Evaluation + Pawn Structure + Bishop Pair + King Safety
 * - Forced Mate-in-N detection & cooperative cancellation
 */
class LocalTacticalEngine {

    private companion object {
        const val INF = 100_000
        const val MATE_SCORE = 50_000
        const val MATE_THRESHOLD = 48_000
        const val TT_SIZE = 1 shl 16 // 65,536 entries (~2 MB RAM, mid-range friendly)
        const val TT_MASK = TT_SIZE - 1

        const val FLAG_EXACT = 0
        const val FLAG_LOWER = 1
        const val FLAG_UPPER = 2

        // PeSTO Middlegame & Endgame Material Values
        val MG_VALUE = intArrayOf(82, 337, 365, 477, 1025, 0)
        val EG_VALUE = intArrayOf(94, 281, 297, 512, 936, 0)
        val PHASE_INC = intArrayOf(0, 1, 1, 2, 4, 0)

        // PeSTO Piece-Square Tables (indexed from rank 8 down to rank 1, file a..h for White)
        val MG_PAWN = intArrayOf(
            0,   0,   0,   0,   0,   0,  0,   0,
            98, 134,  61,  95,  68, 126, 34, -11,
            -6,   7,  26,  31,  65,  56, 25, -20,
            -14,  13,   6,  21,  23,  12, 17, -23,
            -27,  -2,  -5,  12,  17,   6, 10, -25,
            -26,  -4,  -4, -10,   3,   3, 33, -12,
            -35,  -1, -20, -23, -15,  24, 38, -22,
            0,   0,   0,   0,   0,   0,  0,   0
        )
        val EG_PAWN = intArrayOf(
            0,   0,   0,   0,   0,   0,   0,   0,
            178, 173, 158, 134, 147, 132, 165, 187,
            94, 100,  85,  67,  56,  53,  82,  84,
            32,  24,  13,   5,  -2,   4,  17,  17,
            13,   9,  -3,  -7,  -7,  -8,   3,  -1,
            4,   7,  -6,   1,   0,  -5,  -1,  -8,
            13,   8,   8,  10,  13,   0,   2,  -7,
            0,   0,   0,   0,   0,   0,   0,   0
        )
        val MG_KNIGHT = intArrayOf(
            -167, -89, -34, -49,  61, -97, -15, -107,
            -73, -41,  72,  36,  23,  62,   7,  -17,
            -47,  60,  37,  65,  84, 129,  73,   44,
            -9,  17,  19,  53,  37,  69,  18,   22,
            -13,   4,  16,  13,  28,  19,  21,   -8,
            -23,  -9,  12,  10,  19,  17,  25,  -16,
            -29, -53, -12,  -3,  -1,  18, -14,  -19,
            -105, -21, -58, -33, -17, -28, -19,  -23
        )
        val EG_KNIGHT = intArrayOf(
            -58, -38, -13, -28, -31, -27, -63, -99,
            -25,  -8, -25,  -2,  -9, -25, -24, -52,
            -24, -20,  10,   9,  -1,  -9, -19, -41,
            -17,   3,  22,  22,  22,  11,   8, -18,
            -18,  -6,  16,  25,  16,  17,   4, -18,
            -23,  -3,  -1,  15,  10,  -3, -20, -22,
            -42, -20, -10,  -5,  -2, -20, -23, -44,
            -29, -51, -23, -15, -22, -18, -50, -64
        )
        val MG_BISHOP = intArrayOf(
            -29,   4, -82, -37, -25, -42,   7,  -8,
            -26,  16, -18, -13,  30,  59,  18, -47,
            -16,  37,  43,  40,  35,  50,  37,  -2,
            -4,   5,  19,  50,  37,  37,   7,  -2,
            -6,  13,  13,  26,  34,  12,  10,   4,
            0,  15,  15,  15,  14,  27,  18,  10,
            4,  15,  16,   0,   7,  21,  33,   1,
            -33,  -3, -14, -21, -13, -12, -39, -21
        )
        val EG_BISHOP = intArrayOf(
            -14, -21, -11,  -8, -7,  -9, -17, -24,
            -8,  -4,   7, -12, -3, -13,  -4, -14,
            2,  -8,   0,  -1, -2,   6,   0,   4,
            -3,   9,  12,   9, 14,  10,   3,   2,
            -6,   3,  13,  19,  7,  10,  -3,  -9,
            -12,  -3,   8,  10, 13,   3,  -7, -15,
            -14, -18,  -7,  -1,  4,  -9, -15, -27,
            -23,  -9, -23,  -5, -9, -16,  -5, -17
        )
        val MG_ROOK = intArrayOf(
            32,  42,  32,  51, 63,  9,  31,  43,
            27,  32,  58,  62, 80, 67,  26,  44,
            -5,  19,  26,  36, 17, 45,  61,  16,
            -24, -11,   7,  26, 24, 35,  -8, -20,
            -36, -26, -12,  -1,  9, -7,   6, -23,
            -45, -25, -16, -17,  3,  0,  -5, -33,
            -44, -16, -20,  -9, -1, 11,  -6, -71,
            -19, -13,   1,  17, 16,  7, -37, -26
        )
        val EG_ROOK = intArrayOf(
            13, 10, 18, 15, 12,  12,   8,   5,
            11, 13, 13, 11, -3,   3,   8,   3,
            7,  7,  7,  5,  4,  -3,  -5,  -3,
            4,  3, 13,  1,  2,   1,  -1,   2,
            3,  5,  8,  4, -5,  -6,  -8, -11,
            -4,  0, -5, -1, -7, -12,  -8, -16,
            -6, -6,  0,  2, -9,  -9, -11,  -3,
            -9,  2,  3, -1, -5, -13,   4, -20
        )
        val MG_QUEEN = intArrayOf(
            -28,   0,  29,  12,  59,  44,  43,  45,
            -24, -39,  -5,   1, -16,  57,  28,  54,
            -13, -17,   7,   8,  29,  56,  47,  57,
            -27, -27, -16, -16,  -1,  17,  -2,   1,
            -9, -26,  -9, -10,  -2,  -4,   3,  -3,
            -14,   2, -11,  -2,  -5,   2,  14,   5,
            -35,  -8,  11,   2,   8,  15,  -3,   1,
            -1, -18,  -9,  10, -15, -25, -31, -50
        )
        val EG_QUEEN = intArrayOf(
            -9,  22,  22,  27,  27,  19,  10,  20,
            -17,  20,  32,  41,  58,  25,  30,   0,
            -20,   6,   9,  49,  47,  35,  19,   9,
            3,  22,  24,  45,  57,  40,  57,  36,
            -18,  28,  19,  47,  31,  34,  39,  23,
            -16, -27,  15,   6,   9,  17,  10,   5,
            -22, -23, -30, -16, -16, -23, -36, -32,
            -33, -28, -22, -43,  -5, -32, -20, -41
        )
        val MG_KING = intArrayOf(
            -65,  23,  16, -15, -56, -34,   2,  13,
            29,  -1, -20,  -7,  -8,  -4, -38, -29,
            -9,  24,   2, -16, -20,   6,  22, -22,
            -17, -20, -12, -27, -30, -25, -14, -36,
            -49,  -1, -27, -39, -46, -44, -33, -51,
            -14, -14, -22, -46, -44, -30, -15, -27,
            1,   7,  -8, -64, -43, -16,   9,   8,
            -15,  36,  12, -54,   8, -28,  24,  14
        )
        val EG_KING = intArrayOf(
            -74, -35, -18, -18, -11,  15,   4, -17,
            -12,  17,  14,  17,  17,  38,  23,  11,
            10,  17,  23,  15,  20,  45,  44,  13,
            -8,  22,  24,  27,  26,  33,  26,   3,
            -18,  -4,  21,  24,  27,  23,   9, -11,
            -19,  -3,  11,  21,  23,  16,   7,  -9,
            -27, -11,   4,  13,  14,   4,  -5, -17,
            -53, -34, -21, -11, -28, -14, -24, -43
        )

        val MG_TABLES = arrayOf(MG_PAWN, MG_KNIGHT, MG_BISHOP, MG_ROOK, MG_QUEEN, MG_KING)
        val EG_TABLES = arrayOf(EG_PAWN, EG_KNIGHT, EG_BISHOP, EG_ROOK, EG_QUEEN, EG_KING)
    }

    private val ttHash = LongArray(TT_SIZE)
    private val ttDepth = ByteArray(TT_SIZE)
    private val ttFlag = ByteArray(TT_SIZE)
    private val ttScore = IntArray(TT_SIZE)
    private val ttBestUci = arrayOfNulls<String>(TT_SIZE)

    private val killer1 = arrayOfNulls<String>(32)
    private val killer2 = arrayOfNulls<String>(32)

    private var nodesSearched: Long = 0L
    private var deadlineMs: Long = Long.MAX_VALUE
    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    fun clearHash() {
        ttHash.fill(0L)
        ttBestUci.fill(null)
    }

    fun analyzePosition(
        board: ChessBoardState,
        maxDepth: Int = 5,
        timeLimitMs: Long = 900L,
        multiPv: Int = 3
    ): EngineEvaluation {
        cancelled.set(false)
        nodesSearched = 0L
        deadlineMs = System.currentTimeMillis() + timeLimitMs
        killer1.fill(null)
        killer2.fill(null)

        val fen = board.toFen()
        val legalMoves = board.generateLegalMoves()

        if (legalMoves.isEmpty()) {
            val score = if (board.isInCheck(board.sideToMove)) {
                EvalScore.fromSideToMove(-MATE_SCORE, -0, board.sideToMove)
            } else {
                EvalScore(0, null)
            }
            return EngineEvaluation(
                fen = fen,
                score = score,
                bestMoveUci = null,
                bestMoveSan = null,
                pvLines = emptyList(),
                depth = 0,
                nodes = 1,
                engineName = "Local Tactical Engine (PeSTO Multi-PV)"
            )
        }

        val numPv = min(multiPv.coerceAtLeast(1), legalMoves.size)
        var bestLines = emptyList<PvLine>()
        var completedDepth = 1

        // Iterative deepening
        for (depth in 1..maxDepth.coerceIn(1, 8)) {
            if (isStopped()) break

            val excludedMoves = mutableSetOf<String>()
            val depthLines = mutableListOf<PvLine>()

            for (pvIdx in 1..numPv) {
                if (isStopped() && depth > 1) break
                val candidates = legalMoves.filter { it.toUci() !in excludedMoves }
                if (candidates.isEmpty()) break

                val result = searchRoot(board, candidates, depth)
                if (isStopped() && depth > 1) break

                val bestMove = result.first ?: break
                val rawScore = result.second
                excludedMoves.add(bestMove.toUci())

                val pvUcis = extractPv(board, bestMove, depth + 2)
                val pvSans = convertPvToSan(board, pvUcis)
                val evalScore = rawToEvalScore(rawScore, board.sideToMove)
                depthLines.add(
                    PvLine(
                        multiPvIndex = pvIdx,
                        score = evalScore,
                        uciMoves = pvUcis,
                        sanMoves = pvSans,
                        depth = depth
                    )
                )
            }

            if (depthLines.isNotEmpty()) {
                // Sort lines from side-to-move's best to worst
                val sorted = depthLines.sortedByDescending { it.score.toPerspectiveCp(board.sideToMove) }
                    .mapIndexed { idx, line -> line.copy(multiPvIndex = idx + 1) }
                bestLines = sorted
                completedDepth = depth
            }

            if (System.currentTimeMillis() >= deadlineMs) break
        }

        val topLine = bestLines.firstOrNull()
        val bestUci = topLine?.uciMoves?.firstOrNull()
        val bestSan = topLine?.sanMoves?.firstOrNull()
        val secondBestScore = bestLines.getOrNull(1)?.score

        return EngineEvaluation(
            fen = fen,
            score = topLine?.score ?: EvalScore(0),
            bestMoveUci = bestUci,
            bestMoveSan = bestSan,
            secondBestScore = secondBestScore,
            pvLines = bestLines,
            depth = completedDepth,
            nodes = nodesSearched,
            engineName = "Local Tactical Engine (PeSTO Multi-PV)"
        )
    }

    private fun isStopped(): Boolean {
        if (cancelled.get()) return true
        if ((nodesSearched and 2047L) == 0L && System.currentTimeMillis() >= deadlineMs) {
            return true
        }
        return false
    }

    private fun searchRoot(
        board: ChessBoardState,
        candidates: List<ChessMove>,
        depth: Int
    ): Pair<ChessMove?, Int> {
        val hash = board.zobristHash()
        val ttIdx = (hash and TT_MASK.toLong()).toInt()
        val ttMove = if (ttHash[ttIdx] == hash) ttBestUci[ttIdx] else null

        val ordered = orderMoves(board, candidates, ttMove, 0)
        var alpha = -INF
        val beta = INF
        var bestMove: ChessMove? = ordered.firstOrNull()
        var bestScore = -INF

        for (move in ordered) {
            val child = board.applyMoveUnchecked(move)
            val score = -negamax(child, depth - 1, -beta, -alpha, 1, allowNull = true)
            if (isStopped() && depth > 1) break
            if (score > bestScore) {
                bestScore = score
                bestMove = move
                if (score > alpha) alpha = score
            }
        }
        if (bestMove != null && !isStopped()) {
            storeTt(hash, depth, FLAG_EXACT, bestScore, bestMove.toUci())
        }
        return bestMove to bestScore
    }

    private fun negamax(
        board: ChessBoardState,
        depth: Int,
        alphaInit: Int,
        beta: Int,
        ply: Int,
        allowNull: Boolean
    ): Int {
        nodesSearched++
        if (isStopped()) return 0

        if (board.halfMoveClock >= 100 || board.isInsufficientMaterial()) {
            return 0
        }

        val inCheck = board.isInCheck(board.sideToMove)
        val effectiveDepth = if (inCheck && ply < 14) depth + 1 else depth

        if (effectiveDepth <= 0) {
            return quiescence(board, alphaInit, beta, 0)
        }

        val hash = board.zobristHash()
        val ttIdx = (hash and TT_MASK.toLong()).toInt()
        var ttMove: String? = null
        var alpha = alphaInit

        if (ttHash[ttIdx] == hash) {
            ttMove = ttBestUci[ttIdx]
            if (ttDepth[ttIdx].toInt() >= effectiveDepth) {
                var cachedScore = ttScore[ttIdx]
                if (cachedScore > MATE_THRESHOLD) cachedScore -= ply
                else if (cachedScore < -MATE_THRESHOLD) cachedScore += ply
                when (ttFlag[ttIdx].toInt()) {
                    FLAG_EXACT -> return cachedScore
                    FLAG_LOWER -> if (cachedScore >= beta) return cachedScore
                    FLAG_UPPER -> if (cachedScore <= alpha) return cachedScore
                }
            }
        }

        // Null-Move Pruning (safe when not in check and sufficient non-pawn material)
        if (allowNull && !inCheck && effectiveDepth >= 3 && board.sideNonPawnMaterialPoints(board.sideToMove) >= 6) {
            val nullBoard = ChessBoardState(
                squares = board.squares,
                sideToMove = board.sideToMove.opposite(),
                castling = board.castling,
                enPassantSquare = null,
                halfMoveClock = board.halfMoveClock + 1,
                fullMoveNumber = board.fullMoveNumber
            )
            val nullScore = -negamax(nullBoard, effectiveDepth - 3, -beta, -beta + 1, ply + 1, allowNull = false)
            if (nullScore >= beta && nullScore < MATE_THRESHOLD) {
                return beta
            }
        }

        val legalMoves = board.generateLegalMoves()
        if (legalMoves.isEmpty()) {
            return if (inCheck) -(MATE_SCORE - ply) else 0
        }

        val ordered = orderMoves(board, legalMoves, ttMove, ply)
        var bestScore = -INF
        var bestMoveUci: String? = null
        val origAlpha = alpha

        for (i in ordered.indices) {
            val move = ordered[i]
            val child = board.applyMoveUnchecked(move)

            var score: Int
            // Principal Variation Search + gentle Late Move Reduction
            if (i == 0) {
                score = -negamax(child, effectiveDepth - 1, -beta, -alpha, ply + 1, allowNull = true)
            } else {
                val isQuiet = move.capturedPiece == null && move.promotion == null && !inCheck
                val reduction = if (i >= 4 && effectiveDepth >= 3 && isQuiet) 1 else 0
                score = -negamax(child, effectiveDepth - 1 - reduction, -alpha - 1, -alpha, ply + 1, allowNull = true)
                if (score > alpha && (reduction > 0 || score < beta)) {
                    score = -negamax(child, effectiveDepth - 1, -beta, -alpha, ply + 1, allowNull = true)
                }
            }

            if (isStopped()) return 0

            if (score > bestScore) {
                bestScore = score
                bestMoveUci = move.toUci()
                if (score > alpha) {
                    alpha = score
                    if (alpha >= beta) {
                        if (move.capturedPiece == null && ply < killer1.size) {
                            killer2[ply] = killer1[ply]
                            killer1[ply] = move.toUci()
                        }
                        break
                    }
                }
            }
        }

        val flag = when {
            bestScore <= origAlpha -> FLAG_UPPER
            bestScore >= beta -> FLAG_LOWER
            else -> FLAG_EXACT
        }
        var storedScore = bestScore
        if (storedScore > MATE_THRESHOLD) storedScore += ply
        else if (storedScore < -MATE_THRESHOLD) storedScore -= ply
        storeTt(hash, effectiveDepth, flag, storedScore, bestMoveUci)

        return bestScore
    }

    private fun quiescence(board: ChessBoardState, alphaInit: Int, beta: Int, qPly: Int): Int {
        nodesSearched++
        if (isStopped()) return 0

        val standPat = evaluate(board)
        if (qPly >= 6) return standPat
        if (standPat >= beta) return beta
        var alpha = max(alphaInit, standPat)

        // Generate tactical moves only (captures & queen promotions)
        val tacticalMoves = board.generateLegalMoves()
            .filter { it.capturedPiece != null || it.promotion == PieceType.QUEEN }
            .sortedByDescending { captureScore(board, it) }

        for (move in tacticalMoves) {
            // Delta pruning for hopeless captures
            val gain = (move.capturedPiece?.type?.valueCp ?: 0) + (if (move.promotion != null) 800 else 0)
            if (standPat + gain + 200 < alpha) continue

            val child = board.applyMoveUnchecked(move)
            val score = -quiescence(child, -beta, -alpha, qPly + 1)
            if (isStopped()) return 0
            if (score >= beta) return beta
            if (score > alpha) alpha = score
        }
        return alpha
    }

    private fun orderMoves(
        board: ChessBoardState,
        moves: List<ChessMove>,
        ttMove: String?,
        ply: Int
    ): List<ChessMove> {
        val k1 = if (ply < killer1.size) killer1[ply] else null
        val k2 = if (ply < killer2.size) killer2[ply] else null
        return moves.sortedByDescending { move ->
            val uci = move.toUci()
            when {
                uci == ttMove -> 100_000
                move.capturedPiece != null -> 50_000 + captureScore(board, move)
                move.promotion != null -> 45_000 + move.promotion.valueCp
                uci == k1 -> 30_000
                uci == k2 -> 25_000
                move.isCastleKingSide || move.isCastleQueenSide -> 15_000
                else -> 0
            }
        }
    }

    private fun captureScore(board: ChessBoardState, move: ChessMove): Int {
        val victim = move.capturedPiece?.type?.valueCp ?: 0
        val attacker = board.pieceAt(move.from)?.type?.valueCp ?: 100
        return victim * 10 - attacker
    }

    private fun storeTt(hash: Long, depth: Int, flag: Int, score: Int, bestMoveUci: String?) {
        val idx = (hash and TT_MASK.toLong()).toInt()
        if (ttHash[idx] == 0L || depth >= ttDepth[idx].toInt()) {
            ttHash[idx] = hash
            ttDepth[idx] = depth.toByte()
            ttFlag[idx] = flag.toByte()
            ttScore[idx] = score
            if (bestMoveUci != null) ttBestUci[idx] = bestMoveUci
        }
    }

    /**
     * PeSTO Tapered Evaluation + Pawn Structure + Bishop Pair + King Safety.
     * Returns score from [board.sideToMove]'s perspective in centipawns.
     */
    fun evaluate(board: ChessBoardState): Int {
        var mgWhite = 0; var mgBlack = 0
        var egWhite = 0; var egBlack = 0
        var gamePhase = 0

        var whiteBishops = 0; var blackBishops = 0
        val whitePawnFiles = IntArray(8)
        val blackPawnFiles = IntArray(8)
        val whitePawnMaxRank = IntArray(8) { -1 }
        val blackPawnMinRank = IntArray(8) { 8 }

        for (sq in 0..63) {
            val p = board.squares[sq] ?: continue
            val typeIdx = p.type.ordinal
            val file = sq and 7
            val rank = sq ushr 3

            // PeSTO table index: rank 7 (rank 8) -> row 0, rank 0 (rank 1) -> row 7 for White
            val whiteTblIdx = (7 - rank) * 8 + file
            val blackTblIdx = rank * 8 + file

            gamePhase += PHASE_INC[typeIdx]

            if (p.color == PieceColor.WHITE) {
                mgWhite += MG_VALUE[typeIdx] + MG_TABLES[typeIdx][whiteTblIdx]
                egWhite += EG_VALUE[typeIdx] + EG_TABLES[typeIdx][whiteTblIdx]
                if (p.type == PieceType.BISHOP) whiteBishops++
                if (p.type == PieceType.PAWN) {
                    whitePawnFiles[file]++
                    if (rank > whitePawnMaxRank[file]) whitePawnMaxRank[file] = rank
                }
            } else {
                mgBlack += MG_VALUE[typeIdx] + MG_TABLES[typeIdx][blackTblIdx]
                egBlack += EG_VALUE[typeIdx] + EG_TABLES[typeIdx][blackTblIdx]
                if (p.type == PieceType.BISHOP) blackBishops++
                if (p.type == PieceType.PAWN) {
                    blackPawnFiles[file]++
                    if (rank < blackPawnMinRank[file]) blackPawnMinRank[file] = rank
                }
            }
        }

        // Bishop pair bonus
        if (whiteBishops >= 2) { mgWhite += 30; egWhite += 45 }
        if (blackBishops >= 2) { mgBlack += 30; egBlack += 45 }

        // Pawn structure: doubled, isolated, and passed pawns
        for (f in 0..7) {
            if (whitePawnFiles[f] > 1) {
                mgWhite -= 12 * (whitePawnFiles[f] - 1)
                egWhite -= 18 * (whitePawnFiles[f] - 1)
            }
            if (blackPawnFiles[f] > 1) {
                mgBlack -= 12 * (blackPawnFiles[f] - 1)
                egBlack -= 18 * (blackPawnFiles[f] - 1)
            }
            if (whitePawnFiles[f] > 0) {
                val left = if (f > 0) whitePawnFiles[f - 1] else 0
                val right = if (f < 7) whitePawnFiles[f + 1] else 0
                if (left == 0 && right == 0) { mgWhite -= 14; egWhite -= 16 }
                val r = whitePawnMaxRank[f]
                val blockedByBlack = (blackPawnMinRank[f] > r && blackPawnMinRank[f] < 8) ||
                    (f > 0 && blackPawnMinRank[f - 1] > r && blackPawnMinRank[f - 1] < 8) ||
                    (f < 7 && blackPawnMinRank[f + 1] > r && blackPawnMinRank[f + 1] < 8)
                if (!blockedByBlack && r >= 3) {
                    val bonus = (r - 2) * (r - 2) * 5
                    mgWhite += bonus / 2
                    egWhite += bonus
                }
            }
            if (blackPawnFiles[f] > 0) {
                val left = if (f > 0) blackPawnFiles[f - 1] else 0
                val right = if (f < 7) blackPawnFiles[f + 1] else 0
                if (left == 0 && right == 0) { mgBlack -= 14; egBlack -= 16 }
                val r = blackPawnMinRank[f]
                val blockedByWhite = (whitePawnMaxRank[f] < r && whitePawnMaxRank[f] >= 0) ||
                    (f > 0 && whitePawnMaxRank[f - 1] < r && whitePawnMaxRank[f - 1] >= 0) ||
                    (f < 7 && whitePawnMaxRank[f + 1] < r && whitePawnMaxRank[f + 1] >= 0)
                if (!blockedByWhite && r <= 4) {
                    val adv = 5 - r
                    val bonus = adv * adv * 5
                    mgBlack += bonus / 2
                    egBlack += bonus
                }
            }
        }

        // Rook on open / semi-open files
        for (sq in 0..63) {
            val p = board.squares[sq] ?: continue
            if (p.type == PieceType.ROOK) {
                val f = sq and 7
                if (p.color == PieceColor.WHITE) {
                    if (whitePawnFiles[f] == 0) {
                        val b = if (blackPawnFiles[f] == 0) 22 else 11
                        mgWhite += b; egWhite += b / 2
                    }
                } else {
                    if (blackPawnFiles[f] == 0) {
                        val b = if (whitePawnFiles[f] == 0) 22 else 11
                        mgBlack += b; egBlack += b / 2
                    }
                }
            }
        }

        val mgPhase = min(gamePhase, 24)
        val egPhase = 24 - mgPhase
        val mgDiff = mgWhite - mgBlack
        val egDiff = egWhite - egBlack
        val whiteScore = (mgDiff * mgPhase + egDiff * egPhase) / 24

        // Small tempo bonus for side to move
        val tempo = 10
        return if (board.sideToMove == PieceColor.WHITE) whiteScore + tempo else -whiteScore + tempo
    }

    private fun extractPv(root: ChessBoardState, firstMove: ChessMove, maxLen: Int): List<String> {
        val result = mutableListOf(firstMove.toUci())
        var cur = root.applyMoveUnchecked(firstMove)
        val seen = hashSetOf(root.zobristHash(), cur.zobristHash())

        repeat(maxLen - 1) {
            val h = cur.zobristHash()
            val idx = (h and TT_MASK.toLong()).toInt()
            if (ttHash[idx] != h) return result
            val uci = ttBestUci[idx] ?: return result
            val applied = cur.applyUciMove(uci) ?: return result
            val nextHash = applied.second.zobristHash()
            if (!seen.add(nextHash)) return result
            result.add(uci)
            cur = applied.second
        }
        return result
    }

    private fun convertPvToSan(root: ChessBoardState, uciMoves: List<String>): List<String> {
        val sans = mutableListOf<String>()
        var cur = root
        for (uci in uciMoves) {
            val legal = cur.generateLegalMoves()
            val matched = legal.firstOrNull { it.toUci() == uci } ?: break
            sans.add(cur.moveToSan(matched, legal))
            cur = cur.applyMoveUnchecked(matched)
        }
        return sans
    }

    private fun rawToEvalScore(rawFromMover: Int, sideToMove: PieceColor): EvalScore {
        return if (abs(rawFromMover) >= MATE_THRESHOLD) {
            val pliesToMate = (MATE_SCORE - abs(rawFromMover)).coerceAtLeast(1)
            val fullMovesToMate = (pliesToMate + 1) / 2
            val signedMate = if (rawFromMover > 0) fullMovesToMate else -fullMovesToMate
            EvalScore.fromSideToMove(0, signedMate, sideToMove)
        } else {
            EvalScore.fromSideToMove(rawFromMover, null, sideToMove)
        }
    }
}
