package com.example.chess.core

enum class PieceColor {
    WHITE, BLACK;

    fun opposite(): PieceColor = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val symbol: Char, val valueCp: Int, val phasePoints: Int) {
    PAWN('P', 100, 0),
    KNIGHT('N', 320, 3),
    BISHOP('B', 330, 3),
    ROOK('R', 500, 5),
    QUEEN('Q', 900, 9),
    KING('K', 20000, 0);

    companion object {
        fun fromChar(c: Char): PieceType? = when (c.uppercaseChar()) {
            'P' -> PAWN
            'N' -> KNIGHT
            'B' -> BISHOP
            'R' -> ROOK
            'Q' -> QUEEN
            'K' -> KING
            else -> null
        }
    }
}

data class Piece(
    val color: PieceColor,
    val type: PieceType
) {
    fun fenChar(): Char = if (color == PieceColor.WHITE) type.symbol else type.symbol.lowercaseChar()

    companion object {
        fun fromFenChar(c: Char): Piece? {
            val type = PieceType.fromChar(c) ?: return null
            val color = if (c.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
            return Piece(color, type)
        }
    }
}

/**
 * Square index: 0..63 where 0 = a1, 7 = h1, 56 = a8, 63 = h8.
 * rank = index / 8 (0 = rank 1, 7 = rank 8)
 * file = index % 8 (0 = file a, 7 = file h)
 */
@JvmInline
value class Square(val index: Int) {
    val file: Int get() = index and 7
    val rank: Int get() = index ushr 3

    fun toAlgebraic(): String {
        val f = ('a' + file)
        val r = ('1' + rank)
        return "$f$r"
    }

    companion object {
        fun of(file: Int, rank: Int): Square = Square(rank * 8 + file)

        fun fromAlgebraic(s: String): Square? {
            if (s.length != 2) return null
            val file = s[0] - 'a'
            val rank = s[1] - '1'
            if (file !in 0..7 || rank !in 0..7) return null
            return of(file, rank)
        }

        fun isValid(file: Int, rank: Int): Boolean = file in 0..7 && rank in 0..7
    }
}

data class CastlingRights(
    val whiteKingSide: Boolean = true,
    val whiteQueenSide: Boolean = true,
    val blackKingSide: Boolean = true,
    val blackQueenSide: Boolean = true
) {
    fun toFen(): String {
        val sb = StringBuilder(4)
        if (whiteKingSide) sb.append('K')
        if (whiteQueenSide) sb.append('Q')
        if (blackKingSide) sb.append('k')
        if (blackQueenSide) sb.append('q')
        return if (sb.isEmpty()) "-" else sb.toString()
    }

    companion object {
        val NONE = CastlingRights(false, false, false, false)

        fun fromFen(s: String): CastlingRights? {
            if (s == "-") return NONE
            if (s.isEmpty() || s.length > 4) return null
            var wk = false; var wq = false; var bk = false; var bq = false
            for (c in s) {
                when (c) {
                    'K' -> if (wk) return null else wk = true
                    'Q' -> if (wq) return null else wq = true
                    'k' -> if (bk) return null else bk = true
                    'q' -> if (bq) return null else bq = true
                    else -> return null
                }
            }
            return CastlingRights(wk, wq, bk, bq)
        }
    }
}

data class ChessMove(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
    val isCastleKingSide: Boolean = false,
    val isCastleQueenSide: Boolean = false,
    val isEnPassant: Boolean = false,
    val capturedPiece: Piece? = null
) {
    fun toUci(): String {
        val promo = promotion?.symbol?.lowercaseChar()?.toString().orEmpty()
        return "${from.toAlgebraic()}${to.toAlgebraic()}$promo"
    }
}

enum class PositionStatus {
    IN_PROGRESS,
    CHECK,
    CHECKMATE,
    STALEMATE,
    DRAW_FIFTY_MOVE,
    DRAW_INSUFFICIENT_MATERIAL
}

sealed class FenValidationResult {
    data class Valid(val state: ChessBoardState) : FenValidationResult()
    data class Invalid(val reason: String) : FenValidationResult()
}
