package com.example.chess.core

import kotlin.math.abs

/**
 * Complete, immutable chess board position supporting legal move generation,
 * SAN/UCI conversion, strict FEN parsing/validation, and Zobrist hashing.
 */
class ChessBoardState(
    val squares: Array<Piece?>,
    val sideToMove: PieceColor = PieceColor.WHITE,
    val castling: CastlingRights = CastlingRights(),
    val enPassantSquare: Square? = null,
    val halfMoveClock: Int = 0,
    val fullMoveNumber: Int = 1
) {
    init {
        require(squares.size == 64) { "Board must contain 64 squares" }
    }

    fun pieceAt(sq: Square): Piece? = squares[sq.index]
    fun pieceAt(index: Int): Piece? = squares[index]
    fun pieceAt(file: Int, rank: Int): Piece? = if (Square.isValid(file, rank)) squares[rank * 8 + file] else null

    fun kingSquare(color: PieceColor): Square? {
        for (i in 0..63) {
            val p = squares[i]
            if (p != null && p.color == color && p.type == PieceType.KING) return Square(i)
        }
        return null
    }

    fun isInCheck(color: PieceColor = sideToMove): Boolean {
        val kingSq = kingSquare(color) ?: return false
        return isSquareAttackedBy(kingSq.index, color.opposite())
    }

    fun isSquareAttackedBy(sqIdx: Int, attacker: PieceColor): Boolean {
        val file = sqIdx and 7
        val rank = sqIdx ushr 3

        // 1. Pawn attacks
        val pawnSourceRank = if (attacker == PieceColor.WHITE) rank - 1 else rank + 1
        if (pawnSourceRank in 0..7) {
            if (file > 0) {
                val p = squares[pawnSourceRank * 8 + (file - 1)]
                if (p != null && p.color == attacker && p.type == PieceType.PAWN) return true
            }
            if (file < 7) {
                val p = squares[pawnSourceRank * 8 + (file + 1)]
                if (p != null && p.color == attacker && p.type == PieceType.PAWN) return true
            }
        }

        // 2. Knight attacks
        for (i in 0 until 8) {
            val nf = file + KNIGHT_DF[i]
            val nr = rank + KNIGHT_DR[i]
            if (nf in 0..7 && nr in 0..7) {
                val p = squares[nr * 8 + nf]
                if (p != null && p.color == attacker && p.type == PieceType.KNIGHT) return true
            }
        }

        // 3. King attacks
        for (i in 0 until 8) {
            val kf = file + KING_DF[i]
            val kr = rank + KING_DR[i]
            if (kf in 0..7 && kr in 0..7) {
                val p = squares[kr * 8 + kf]
                if (p != null && p.color == attacker && p.type == PieceType.KING) return true
            }
        }

        // 4. Orthogonal sliders (Rook / Queen)
        for (d in 0 until 4) {
            var f = file + ORTHO_DF[d]
            var r = rank + ORTHO_DR[d]
            while (f in 0..7 && r in 0..7) {
                val p = squares[r * 8 + f]
                if (p != null) {
                    if (p.color == attacker && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) return true
                    break
                }
                f += ORTHO_DF[d]
                r += ORTHO_DR[d]
            }
        }

        // 5. Diagonal sliders (Bishop / Queen)
        for (d in 0 until 4) {
            var f = file + DIAG_DF[d]
            var r = rank + DIAG_DR[d]
            while (f in 0..7 && r in 0..7) {
                val p = squares[r * 8 + f]
                if (p != null) {
                    if (p.color == attacker && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) return true
                    break
                }
                f += DIAG_DF[d]
                r += DIAG_DR[d]
            }
        }

        return false
    }

    fun generateLegalMoves(): List<ChessMove> {
        val pseudo = generatePseudoLegalMoves()
        val legal = ArrayList<ChessMove>(pseudo.size)
        for (move in pseudo) {
            val next = applyMoveUnchecked(move)
            if (!next.isInCheck(sideToMove)) {
                legal.add(move)
            }
        }
        return legal
    }

    fun generateLegalMovesFrom(from: Square): List<ChessMove> {
        return generateLegalMoves().filter { it.from == from }
    }

    private fun generatePseudoLegalMoves(): List<ChessMove> {
        val moves = ArrayList<ChessMove>(48)
        val us = sideToMove
        val them = us.opposite()

        for (sqIdx in 0..63) {
            val piece = squares[sqIdx] ?: continue
            if (piece.color != us) continue
            val from = Square(sqIdx)
            val file = sqIdx and 7
            val rank = sqIdx ushr 3

            when (piece.type) {
                PieceType.PAWN -> generatePawnMoves(moves, from, file, rank, us, them)
                PieceType.KNIGHT -> {
                    for (i in 0 until 8) {
                        val nf = file + KNIGHT_DF[i]
                        val nr = rank + KNIGHT_DR[i]
                        if (nf in 0..7 && nr in 0..7) {
                            val targetIdx = nr * 8 + nf
                            val target = squares[targetIdx]
                            if (target == null || target.color == them) {
                                moves.add(ChessMove(from, Square(targetIdx), capturedPiece = target))
                            }
                        }
                    }
                }
                PieceType.BISHOP -> generateSliderMoves(moves, from, file, rank, them, DIAG_DF, DIAG_DR)
                PieceType.ROOK -> generateSliderMoves(moves, from, file, rank, them, ORTHO_DF, ORTHO_DR)
                PieceType.QUEEN -> {
                    generateSliderMoves(moves, from, file, rank, them, ORTHO_DF, ORTHO_DR)
                    generateSliderMoves(moves, from, file, rank, them, DIAG_DF, DIAG_DR)
                }
                PieceType.KING -> {
                    for (i in 0 until 8) {
                        val kf = file + KING_DF[i]
                        val kr = rank + KING_DR[i]
                        if (kf in 0..7 && kr in 0..7) {
                            val targetIdx = kr * 8 + kf
                            val target = squares[targetIdx]
                            if (target == null || target.color == them) {
                                moves.add(ChessMove(from, Square(targetIdx), capturedPiece = target))
                            }
                        }
                    }
                    generateCastlingMoves(moves, us, them)
                }
            }
        }
        return moves
    }

    private fun generatePawnMoves(
        moves: MutableList<ChessMove>,
        from: Square,
        file: Int,
        rank: Int,
        us: PieceColor,
        them: PieceColor
    ) {
        val dir = if (us == PieceColor.WHITE) 1 else -1
        val startRank = if (us == PieceColor.WHITE) 1 else 6
        val promoRank = if (us == PieceColor.WHITE) 7 else 0
        val oneRank = rank + dir

        if (oneRank in 0..7) {
            val oneIdx = oneRank * 8 + file
            if (squares[oneIdx] == null) {
                addPawnMoveOrPromotions(moves, from, Square(oneIdx), oneRank == promoRank, null)
                if (rank == startRank) {
                    val twoRank = rank + dir * 2
                    val twoIdx = twoRank * 8 + file
                    if (squares[twoIdx] == null) {
                        moves.add(ChessMove(from, Square(twoIdx)))
                    }
                }
            }

            // Captures
            for (df in intArrayOf(-1, 1)) {
                val cf = file + df
                if (cf in 0..7) {
                    val capIdx = oneRank * 8 + cf
                    val target = squares[capIdx]
                    if (target != null && target.color == them) {
                        addPawnMoveOrPromotions(moves, from, Square(capIdx), oneRank == promoRank, target)
                    } else if (enPassantSquare != null && enPassantSquare.index == capIdx) {
                        val epCaptured = squares[rank * 8 + cf]
                        if (epCaptured != null && epCaptured.color == them && epCaptured.type == PieceType.PAWN) {
                            moves.add(
                                ChessMove(
                                    from = from,
                                    to = Square(capIdx),
                                    isEnPassant = true,
                                    capturedPiece = epCaptured
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun addPawnMoveOrPromotions(
        moves: MutableList<ChessMove>,
        from: Square,
        to: Square,
        isPromotion: Boolean,
        captured: Piece?
    ) {
        if (isPromotion) {
            for (promo in PROMOTION_TYPES) {
                moves.add(ChessMove(from, to, promotion = promo, capturedPiece = captured))
            }
        } else {
            moves.add(ChessMove(from, to, capturedPiece = captured))
        }
    }

    private fun generateSliderMoves(
        moves: MutableList<ChessMove>,
        from: Square,
        file: Int,
        rank: Int,
        them: PieceColor,
        dfs: IntArray,
        drs: IntArray
    ) {
        for (d in 0 until 4) {
            var f = file + dfs[d]
            var r = rank + drs[d]
            while (f in 0..7 && r in 0..7) {
                val idx = r * 8 + f
                val target = squares[idx]
                if (target == null) {
                    moves.add(ChessMove(from, Square(idx)))
                } else {
                    if (target.color == them) {
                        moves.add(ChessMove(from, Square(idx), capturedPiece = target))
                    }
                    break
                }
                f += dfs[d]
                r += drs[d]
            }
        }
    }

    private fun generateCastlingMoves(moves: MutableList<ChessMove>, us: PieceColor, them: PieceColor) {
        if (us == PieceColor.WHITE) {
            val e1 = 4
            if (squares[e1]?.type == PieceType.KING && squares[e1]?.color == PieceColor.WHITE) {
                if (castling.whiteKingSide &&
                    squares[7] == Piece(PieceColor.WHITE, PieceType.ROOK) &&
                    squares[5] == null && squares[6] == null &&
                    !isSquareAttackedBy(4, them) && !isSquareAttackedBy(5, them) && !isSquareAttackedBy(6, them)
                ) {
                    moves.add(ChessMove(Square(4), Square(6), isCastleKingSide = true))
                }
                if (castling.whiteQueenSide &&
                    squares[0] == Piece(PieceColor.WHITE, PieceType.ROOK) &&
                    squares[1] == null && squares[2] == null && squares[3] == null &&
                    !isSquareAttackedBy(4, them) && !isSquareAttackedBy(3, them) && !isSquareAttackedBy(2, them)
                ) {
                    moves.add(ChessMove(Square(4), Square(2), isCastleQueenSide = true))
                }
            }
        } else {
            val e8 = 60
            if (squares[e8]?.type == PieceType.KING && squares[e8]?.color == PieceColor.BLACK) {
                if (castling.blackKingSide &&
                    squares[63] == Piece(PieceColor.BLACK, PieceType.ROOK) &&
                    squares[61] == null && squares[62] == null &&
                    !isSquareAttackedBy(60, them) && !isSquareAttackedBy(61, them) && !isSquareAttackedBy(62, them)
                ) {
                    moves.add(ChessMove(Square(60), Square(62), isCastleKingSide = true))
                }
                if (castling.blackQueenSide &&
                    squares[56] == Piece(PieceColor.BLACK, PieceType.ROOK) &&
                    squares[57] == null && squares[58] == null && squares[59] == null &&
                    !isSquareAttackedBy(60, them) && !isSquareAttackedBy(59, them) && !isSquareAttackedBy(58, them)
                ) {
                    moves.add(ChessMove(Square(60), Square(58), isCastleQueenSide = true))
                }
            }
        }
    }

    fun applyMoveUnchecked(move: ChessMove): ChessBoardState {
        val nextSquares = squares.copyOf()
        val movingPiece = nextSquares[move.from.index]
            ?: error("No piece on ${move.from.toAlgebraic()}")

        nextSquares[move.from.index] = null

        // Handle en passant capture removal
        if (move.isEnPassant) {
            val capturedSq = move.from.rank * 8 + move.to.file
            nextSquares[capturedSq] = null
        }

        // Place moved or promoted piece
        val placedPiece = if (move.promotion != null) {
            Piece(movingPiece.color, move.promotion)
        } else {
            movingPiece
        }
        nextSquares[move.to.index] = placedPiece

        // Handle castling rook movement
        if (move.isCastleKingSide) {
            if (movingPiece.color == PieceColor.WHITE) {
                nextSquares[7] = null
                nextSquares[5] = Piece(PieceColor.WHITE, PieceType.ROOK)
            } else {
                nextSquares[63] = null
                nextSquares[61] = Piece(PieceColor.BLACK, PieceType.ROOK)
            }
        } else if (move.isCastleQueenSide) {
            if (movingPiece.color == PieceColor.WHITE) {
                nextSquares[0] = null
                nextSquares[3] = Piece(PieceColor.WHITE, PieceType.ROOK)
            } else {
                nextSquares[56] = null
                nextSquares[59] = Piece(PieceColor.BLACK, PieceType.ROOK)
            }
        }

        // Update castling rights
        var wk = castling.whiteKingSide
        var wq = castling.whiteQueenSide
        var bk = castling.blackKingSide
        var bq = castling.blackQueenSide

        if (move.from.index == 4 || move.to.index == 4) { wk = false; wq = false }
        if (move.from.index == 60 || move.to.index == 60) { bk = false; bq = false }
        if (move.from.index == 7 || move.to.index == 7) wk = false
        if (move.from.index == 0 || move.to.index == 0) wq = false
        if (move.from.index == 63 || move.to.index == 63) bk = false
        if (move.from.index == 56 || move.to.index == 56) bq = false

        // Update en passant square
        val newEp = if (movingPiece.type == PieceType.PAWN && abs(move.to.rank - move.from.rank) == 2) {
            val midRank = (move.from.rank + move.to.rank) / 2
            Square.of(move.from.file, midRank)
        } else {
            null
        }

        val isCapture = move.capturedPiece != null || move.isEnPassant || squares[move.to.index] != null
        val newHalfMove = if (movingPiece.type == PieceType.PAWN || isCapture) 0 else halfMoveClock + 1
        val newFullMove = if (sideToMove == PieceColor.BLACK) fullMoveNumber + 1 else fullMoveNumber

        return ChessBoardState(
            squares = nextSquares,
            sideToMove = sideToMove.opposite(),
            castling = CastlingRights(wk, wq, bk, bq),
            enPassantSquare = newEp,
            halfMoveClock = newHalfMove,
            fullMoveNumber = newFullMove
        )
    }

    fun applyUciMove(uci: String): Pair<ChessMove, ChessBoardState>? {
        val cleaned = uci.trim().lowercase()
        if (cleaned.length < 4) return null
        val from = Square.fromAlgebraic(cleaned.substring(0, 2)) ?: return null
        val to = Square.fromAlgebraic(cleaned.substring(2, 4)) ?: return null
        val promo = if (cleaned.length >= 5) PieceType.fromChar(cleaned[4]) else null
        val move = generateLegalMoves().firstOrNull {
            it.from == from && it.to == to && it.promotion == promo
        } ?: return null
        return move to applyMoveUnchecked(move)
    }

    fun moveToSan(move: ChessMove, legalMoves: List<ChessMove> = generateLegalMoves()): String {
        val piece = pieceAt(move.from) ?: return move.toUci()
        val after = applyMoveUnchecked(move)
        val inCheck = after.isInCheck(after.sideToMove)
        val hasReply = if (inCheck) after.generateLegalMoves().isNotEmpty() else true
        val suffix = when {
            inCheck && !hasReply -> "#"
            inCheck -> "+"
            else -> ""
        }

        if (move.isCastleKingSide) return "O-O$suffix"
        if (move.isCastleQueenSide) return "O-O-O$suffix"

        val isCapture = move.capturedPiece != null || move.isEnPassant
        val sb = StringBuilder()

        if (piece.type == PieceType.PAWN) {
            if (isCapture) {
                sb.append('a' + move.from.file)
                sb.append('x')
            }
            sb.append(move.to.toAlgebraic())
            if (move.promotion != null) {
                sb.append('=')
                sb.append(move.promotion.symbol)
            }
        } else {
            sb.append(piece.type.symbol)
            val ambiguous = legalMoves.filter {
                it.from != move.from && it.to == move.to && pieceAt(it.from)?.type == piece.type
            }
            if (ambiguous.isNotEmpty()) {
                val sameFile = ambiguous.any { it.from.file == move.from.file }
                val sameRank = ambiguous.any { it.from.rank == move.from.rank }
                if (!sameFile) {
                    sb.append('a' + move.from.file)
                } else if (!sameRank) {
                    sb.append('1' + move.from.rank)
                } else {
                    sb.append(move.from.toAlgebraic())
                }
            }
            if (isCapture) sb.append('x')
            sb.append(move.to.toAlgebraic())
        }
        sb.append(suffix)
        return sb.toString()
    }

    fun parseSanMove(rawSan: String): ChessMove? {
        val cleaned = rawSan.trim()
            .replace("+", "")
            .replace("#", "")
            .replace("!", "")
            .replace("?", "")
            .replace("0-0-0", "O-O-O")
            .replace("0-0", "O-O")

        if (cleaned.isEmpty()) return null
        val legal = generateLegalMoves()

        if (cleaned == "O-O") return legal.firstOrNull { it.isCastleKingSide }
        if (cleaned == "O-O-O") return legal.firstOrNull { it.isCastleQueenSide }

        // Check promotion suffix like e8=Q or e8Q
        var promo: PieceType? = null
        var body = cleaned
        val eqIdx = body.indexOf('=')
        if (eqIdx >= 0 && eqIdx + 1 < body.length) {
            promo = PieceType.fromChar(body[eqIdx + 1])
            body = body.substring(0, eqIdx)
        } else if (body.length >= 3 && body.last() in charArrayOf('Q', 'R', 'B', 'N') && body[body.length - 2] in charArrayOf('1', '8')) {
            promo = PieceType.fromChar(body.last())
            body = body.dropLast(1)
        }

        body = body.replace("x", "")
        if (body.length < 2) return null

        val destStr = body.takeLast(2)
        val dest = Square.fromAlgebraic(destStr) ?: return null
        val prefix = body.dropLast(2)

        val pieceType: PieceType
        val disambig: String
        if (prefix.isNotEmpty() && prefix[0] in charArrayOf('K', 'Q', 'R', 'B', 'N')) {
            pieceType = PieceType.fromChar(prefix[0]) ?: return null
            disambig = prefix.substring(1)
        } else {
            pieceType = PieceType.PAWN
            disambig = prefix
        }

        var fromFile: Int? = null
        var fromRank: Int? = null
        for (c in disambig) {
            when (c) {
                in 'a'..'h' -> fromFile = c - 'a'
                in '1'..'8' -> fromRank = c - '1'
            }
        }

        return legal.firstOrNull { move ->
            val p = pieceAt(move.from)
            p != null &&
                p.type == pieceType &&
                move.to == dest &&
                move.promotion == promo &&
                (fromFile == null || move.from.file == fromFile) &&
                (fromRank == null || move.from.rank == fromRank)
        }
    }

    fun status(): PositionStatus {
        val legal = generateLegalMoves()
        val inCheck = isInCheck(sideToMove)
        return when {
            legal.isEmpty() && inCheck -> PositionStatus.CHECKMATE
            legal.isEmpty() -> PositionStatus.STALEMATE
            halfMoveClock >= 100 -> PositionStatus.DRAW_FIFTY_MOVE
            isInsufficientMaterial() -> PositionStatus.DRAW_INSUFFICIENT_MATERIAL
            inCheck -> PositionStatus.CHECK
            else -> PositionStatus.IN_PROGRESS
        }
    }

    fun isInsufficientMaterial(): Boolean {
        val nonKings = mutableListOf<Pair<Square, Piece>>()
        for (i in 0..63) {
            val p = squares[i] ?: continue
            if (p.type != PieceType.KING) nonKings.add(Square(i) to p)
        }
        if (nonKings.isEmpty()) return true
        if (nonKings.size == 1) {
            val t = nonKings[0].second.type
            return t == PieceType.BISHOP || t == PieceType.KNIGHT
        }
        if (nonKings.size == 2) {
            val (sq1, p1) = nonKings[0]
            val (sq2, p2) = nonKings[1]
            if (p1.type == PieceType.BISHOP && p2.type == PieceType.BISHOP && p1.color != p2.color) {
                val color1 = (sq1.file + sq1.rank) and 1
                val color2 = (sq2.file + sq2.rank) and 1
                return color1 == color2
            }
        }
        return false
    }

    /** Total non-pawn material points (Q=9, R=5, B=3, N=3). Starting board = 62. */
    fun totalNonPawnMaterialPoints(): Int {
        var total = 0
        for (i in 0..63) {
            val p = squares[i] ?: continue
            total += p.type.phasePoints
        }
        return total
    }

    fun sideNonPawnMaterialPoints(color: PieceColor): Int {
        var total = 0
        for (i in 0..63) {
            val p = squares[i] ?: continue
            if (p.color == color) total += p.type.phasePoints
        }
        return total
    }

    fun materialBalanceCp(perspective: PieceColor): Int {
        var sum = 0
        for (i in 0..63) {
            val p = squares[i] ?: continue
            if (p.type == PieceType.KING) continue
            val v = p.type.valueCp
            sum += if (p.color == perspective) v else -v
        }
        return sum
    }

    fun toFen(): String {
        val sb = StringBuilder()
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val p = squares[rank * 8 + file]
                if (p == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        sb.append(empty)
                        empty = 0
                    }
                    sb.append(p.fenChar())
                }
            }
            if (empty > 0) sb.append(empty)
            if (rank > 0) sb.append('/')
        }
        sb.append(' ')
        sb.append(if (sideToMove == PieceColor.WHITE) 'w' else 'b')
        sb.append(' ')
        sb.append(castling.toFen())
        sb.append(' ')
        sb.append(enPassantSquare?.toAlgebraic() ?: "-")
        sb.append(' ')
        sb.append(halfMoveClock)
        sb.append(' ')
        sb.append(fullMoveNumber)
        return sb.toString()
    }

    fun zobristHash(): Long {
        var h = 0L
        for (i in 0..63) {
            val p = squares[i] ?: continue
            val pIdx = (if (p.color == PieceColor.WHITE) 0 else 6) + p.type.ordinal
            h = h xor ZOBRIST_PIECES[pIdx][i]
        }
        if (sideToMove == PieceColor.BLACK) h = h xor ZOBRIST_SIDE
        var cMask = 0
        if (castling.whiteKingSide) cMask = cMask or 1
        if (castling.whiteQueenSide) cMask = cMask or 2
        if (castling.blackKingSide) cMask = cMask or 4
        if (castling.blackQueenSide) cMask = cMask or 8
        h = h xor ZOBRIST_CASTLING[cMask]
        if (enPassantSquare != null) h = h xor ZOBRIST_EP[enPassantSquare.file]
        return h
    }

    fun withPiece(square: Square, piece: Piece?): ChessBoardState {
        val copy = squares.copyOf()
        copy[square.index] = piece
        return ChessBoardState(copy, sideToMove, castling, enPassantSquare, halfMoveClock, fullMoveNumber)
    }

    fun withSideToMove(color: PieceColor): ChessBoardState =
        ChessBoardState(squares.copyOf(), color, castling, enPassantSquare, halfMoveClock, fullMoveNumber)

    fun withCastling(rights: CastlingRights): ChessBoardState =
        ChessBoardState(squares.copyOf(), sideToMove, rights, enPassantSquare, halfMoveClock, fullMoveNumber)

    companion object {
        const val STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        private val KNIGHT_DF = intArrayOf(-2, -2, -1, -1, 1, 1, 2, 2)
        private val KNIGHT_DR = intArrayOf(-1, 1, -2, 2, -2, 2, -1, 1)
        private val KING_DF = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)
        private val KING_DR = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
        private val ORTHO_DF = intArrayOf(-1, 1, 0, 0)
        private val ORTHO_DR = intArrayOf(0, 0, -1, 1)
        private val DIAG_DF = intArrayOf(-1, -1, 1, 1)
        private val DIAG_DR = intArrayOf(-1, 1, -1, 1)
        private val PROMOTION_TYPES = arrayOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)

        // Deterministic SplitMix64 Zobrist tables
        private val ZOBRIST_PIECES: Array<LongArray>
        private val ZOBRIST_CASTLING: LongArray
        private val ZOBRIST_EP: LongArray
        private val ZOBRIST_SIDE: Long

        init {
            var seed = 0x9E3779B97F4A7C15UL.toLong()
            fun nextLong(): Long {
                seed += 0x9E3779B97F4A7C15UL.toLong()
                var z = seed
                z = (z xor (z ushr 30)) * (-0x40A7B892E31B1A47L)
                z = (z xor (z ushr 27)) * (-0x6B2FB644EC4F0CE5L)
                return z xor (z ushr 31)
            }
            ZOBRIST_PIECES = Array(12) { LongArray(64) { nextLong() } }
            ZOBRIST_CASTLING = LongArray(16) { nextLong() }
            ZOBRIST_EP = LongArray(8) { nextLong() }
            ZOBRIST_SIDE = nextLong()
        }

        fun initial(): ChessBoardState = (fromFen(STARTING_FEN) as FenValidationResult.Valid).state

        fun empty(): ChessBoardState = ChessBoardState(
            squares = arrayOfNulls(64),
            sideToMove = PieceColor.WHITE,
            castling = CastlingRights.NONE,
            enPassantSquare = null,
            halfMoveClock = 0,
            fullMoveNumber = 1
        )

        fun validateBoardForAnalysis(state: ChessBoardState): FenValidationResult {
            var whiteKings = 0
            var blackKings = 0
            for (i in 0..63) {
                val p = state.squares[i] ?: continue
                val rank = i ushr 3
                if (p.type == PieceType.KING) {
                    if (p.color == PieceColor.WHITE) whiteKings++ else blackKings++
                }
                if (p.type == PieceType.PAWN && (rank == 0 || rank == 7)) {
                    return FenValidationResult.Invalid("Pawns cannot be placed on rank 1 or rank 8.")
                }
            }
            if (whiteKings != 1) return FenValidationResult.Invalid("Position must have exactly one White king (found $whiteKings).")
            if (blackKings != 1) return FenValidationResult.Invalid("Position must have exactly one Black king (found $blackKings).")

            // Adjacent kings check
            val wk = state.kingSquare(PieceColor.WHITE)!!
            val bk = state.kingSquare(PieceColor.BLACK)!!
            if (abs(wk.file - bk.file) <= 1 && abs(wk.rank - bk.rank) <= 1) {
                return FenValidationResult.Invalid("Kings cannot be placed on adjacent squares.")
            }

            // Opponent king cannot already be in check when it's our turn
            if (state.isInCheck(state.sideToMove.opposite())) {
                return FenValidationResult.Invalid("Invalid position: side not to move is currently in check.")
            }

            // Sanitize castling rights to match piece positions so engine never gets confused
            val c = state.castling
            val sanitizedCastling = CastlingRights(
                whiteKingSide = c.whiteKingSide && state.squares[4] == Piece(PieceColor.WHITE, PieceType.KING) && state.squares[7] == Piece(PieceColor.WHITE, PieceType.ROOK),
                whiteQueenSide = c.whiteQueenSide && state.squares[4] == Piece(PieceColor.WHITE, PieceType.KING) && state.squares[0] == Piece(PieceColor.WHITE, PieceType.ROOK),
                blackKingSide = c.blackKingSide && state.squares[60] == Piece(PieceColor.BLACK, PieceType.KING) && state.squares[63] == Piece(PieceColor.BLACK, PieceType.ROOK),
                blackQueenSide = c.blackQueenSide && state.squares[60] == Piece(PieceColor.BLACK, PieceType.KING) && state.squares[56] == Piece(PieceColor.BLACK, PieceType.ROOK)
            )

            return FenValidationResult.Valid(state.withCastling(sanitizedCastling))
        }

        fun fromFen(rawFen: String): FenValidationResult {
            val trimmed = rawFen.trim()
            if (trimmed.isEmpty()) return FenValidationResult.Invalid("FEN string is empty.")
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size < 4 || parts.size > 6) {
                return FenValidationResult.Invalid("FEN must contain 4 to 6 space-separated fields.")
            }

            val rankStrings = parts[0].split('/')
            if (rankStrings.size != 8) {
                return FenValidationResult.Invalid("Board layout must have 8 ranks separated by '/'.")
            }

            val squares = arrayOfNulls<Piece>(64)
            for (rIdx in 0..7) {
                val rank = 7 - rIdx
                val rankStr = rankStrings[rIdx]
                var file = 0
                for (ch in rankStr) {
                    if (ch in '1'..'8') {
                        file += (ch - '0')
                    } else {
                        val piece = Piece.fromFenChar(ch)
                            ?: return FenValidationResult.Invalid("Invalid piece character '$ch' in rank ${rank + 1}.")
                        if (file >= 8) {
                            return FenValidationResult.Invalid("Too many squares in rank ${rank + 1}.")
                        }
                        squares[rank * 8 + file] = piece
                        file++
                    }
                }
                if (file != 8) {
                    return FenValidationResult.Invalid("Rank ${rank + 1} has $file squares instead of 8.")
                }
            }

            val side = when (parts[1]) {
                "w" -> PieceColor.WHITE
                "b" -> PieceColor.BLACK
                else -> return FenValidationResult.Invalid("Side to move must be 'w' or 'b'.")
            }

            val castling = CastlingRights.fromFen(parts[2])
                ?: return FenValidationResult.Invalid("Invalid castling rights '${parts[2]}'.")

            val epSquare = if (parts[3] == "-") {
                null
            } else {
                val sq = Square.fromAlgebraic(parts[3])
                    ?: return FenValidationResult.Invalid("Invalid en passant square '${parts[3]}'.")
                if ((side == PieceColor.WHITE && sq.rank != 5) || (side == PieceColor.BLACK && sq.rank != 2)) {
                    return FenValidationResult.Invalid("En passant square '${parts[3]}' is invalid for side to move.")
                }
                sq
            }

            val halfMove = if (parts.size >= 5) {
                parts[4].toIntOrNull()?.takeIf { it >= 0 }
                    ?: return FenValidationResult.Invalid("Half-move clock must be a non-negative integer.")
            } else 0

            val fullMove = if (parts.size >= 6) {
                parts[5].toIntOrNull()?.takeIf { it >= 1 }
                    ?: return FenValidationResult.Invalid("Full-move number must be an integer >= 1.")
            } else 1

            val state = ChessBoardState(squares, side, castling, epSquare, halfMove, fullMove)
            return validateBoardForAnalysis(state)
        }
    }
}
