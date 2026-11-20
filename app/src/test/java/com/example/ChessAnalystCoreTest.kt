package com.example

import com.example.chess.analysis.GameAnalyzer
import com.example.chess.analysis.GamePhase
import com.example.chess.analysis.MoveClassification
import com.example.chess.core.ChessBoardState
import com.example.chess.core.FenValidationResult
import com.example.chess.core.OpeningBook
import com.example.chess.core.PgnParseResult
import com.example.chess.core.PgnParser
import com.example.chess.core.PieceColor
import com.example.chess.core.PositionStatus
import com.example.chess.engine.EvalScore
import com.example.chess.engine.LocalTacticalEngine
import com.example.chess.engine.StockfishUciEngine
import com.example.data.chesscom.ChessComRepository
import com.example.data.chesscom.PerspectiveResult
import com.example.data.chesscom.TimeControlCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessAnalystCoreTest {

    @Test
    fun `initial position generates exactly 20 legal moves and roundtrips FEN`() {
        val board = ChessBoardState.initial()
        assertEquals(20, board.generateLegalMoves().size)
        assertEquals(ChessBoardState.STARTING_FEN, board.toFen())
    }

    @Test
    fun `strict FEN validation rejects invalid boards`() {
        // Missing Black king
        val noBlackKing = ChessBoardState.fromFen("8/8/8/8/8/8/8/4K3 w - - 0 1")
        assertTrue(noBlackKing is FenValidationResult.Invalid)

        // Pawn on rank 8
        val pawnOnEighth = ChessBoardState.fromFen("4k2P/8/8/8/8/8/8/4K3 w - - 0 1")
        assertTrue(pawnOnEighth is FenValidationResult.Invalid)

        // Adjacent kings
        val adjacentKings = ChessBoardState.fromFen("8/8/8/4k3/4K3/8/8/8 w - - 0 1")
        assertTrue(adjacentKings is FenValidationResult.Invalid)
    }

    @Test
    fun `multi-game PGN parsing handles multiple games and detects openings`() {
        val multiPgn = """
            [Event "Game 1"]
            [White "Alice"]
            [Black "Bob"]
            [Result "1-0"]

            1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0

            [Event "Game 2"]
            [White "Carol"]
            [Black "Dave"]
            [Result "1/2-1/2"]

            1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 1/2-1/2
        """.trimIndent()

        val result = PgnParser.parseMultiple(multiPgn)
        assertTrue(result is PgnParseResult.Success)
        val games = (result as PgnParseResult.Success).games
        assertEquals(2, games.size)
        assertEquals(7, games[0].moves.size)
        assertEquals("Qxf7#", games[0].moves.last().san)
        assertTrue(games[1].openingName?.contains("Najdorf") == true)

        // Verify checkmate status at end of Scholar's Mate
        val endBoard = (ChessBoardState.fromFen(games[0].moves.last().fenAfter) as FenValidationResult.Valid).state
        assertEquals(PositionStatus.CHECKMATE, endBoard.status())
    }

    @Test
    fun `incomplete or partially corrupted PGN loads gracefully with warning`() {
        val partialPgn = """
            [White "TestW"]
            [Black "TestB"]
            1. e4 e5 2. Nf3 INVALIDMOVE 3. d4
        """.trimIndent()

        val result = PgnParser.parseMultiple(partialPgn)
        assertTrue(result is PgnParseResult.Success)
        val success = result as PgnParseResult.Success
        assertEquals(1, success.games.size)
        assertEquals(3, success.games[0].moves.size)
        assertTrue(success.warnings.isNotEmpty())
    }

    @Test
    fun `local tactical engine finds forced mate in 1`() {
        // Classic Scholar's Mate setup: White to play Qxf7#
        val fen = "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4"
        val board = (ChessBoardState.fromFen(fen) as FenValidationResult.Valid).state
        val engine = LocalTacticalEngine()
        val eval = engine.analyzePosition(board, maxDepth = 3, timeLimitMs = 1500L, multiPv = 2)

        assertEquals("h5f7", eval.bestMoveUci)
        assertEquals("Qxf7#", eval.bestMoveSan)
        assertNotNull(eval.score.mateIn)
        assertTrue((eval.score.mateIn ?: 0) > 0)
    }

    @Test
    fun `short game reaches Opening only and reports Middlegame and Endgame not reached`() = runTest {
        val pgn = "1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# 1-0"
        val game = (PgnParser.parseMultiple(pgn) as PgnParseResult.Success).games.first()
        val engine = StockfishUciEngine(null)

        val report = GameAnalyzer.analyzeGame(
            game = game,
            engine = engine,
            searchDepth = 3,
            timePerMoveMs = 200L
        )

        assertEquals(7, report.moves.size)
        assertTrue(report.whiteAccuracy in 0.0..100.0)
        assertTrue(report.blackAccuracy in 0.0..100.0)
        assertTrue(report.whiteEstimatedElo in 400..2900)

        val openingPhase = report.phaseSummaries.first { it.phase == GamePhase.OPENING }
        val middlegamePhase = report.phaseSummaries.first { it.phase == GamePhase.MIDDLEGAME }
        val endgamePhase = report.phaseSummaries.first { it.phase == GamePhase.ENDGAME }

        assertTrue(openingPhase.reached)
        assertFalse(middlegamePhase.reached)
        assertFalse(endgamePhase.reached)

        // Annotated PGN export contains accuracy summary and moves
        val exported = PgnParser.exportAnnotatedPgn(report)
        assertTrue(exported.contains("White Accuracy:"))
        assertTrue(exported.contains("Qxf7#"))
    }

    @Test
    fun `win probability curve and accuracy math behave monotonically`() {
        val equalWin = GameAnalyzer.winProbabilityPercent(EvalScore(0), PieceColor.WHITE)
        val advantageWin = GameAnalyzer.winProbabilityPercent(EvalScore(300), PieceColor.WHITE)
        val losingWin = GameAnalyzer.winProbabilityPercent(EvalScore(-300), PieceColor.WHITE)

        assertEquals(50.0, equalWin, 0.5)
        assertTrue(advantageWin > 70.0)
        assertTrue(losingWin < 30.0)

        assertEquals(100.0, GameAnalyzer.moveAccuracyFromWinLoss(0.0), 0.1)
        assertTrue(GameAnalyzer.moveAccuracyFromWinLoss(2.0) > GameAnalyzer.moveAccuracyFromWinLoss(15.0))
        assertTrue(GameAnalyzer.moveAccuracyFromWinLoss(15.0) > GameAnalyzer.moveAccuracyFromWinLoss(35.0))
    }

    @Test
    fun `ChessComRepository formats time controls and perspective results accurately`() {
        assertEquals("3+0" to TimeControlCategory.BLITZ, ChessComRepository.formatTimeControl("180", "blitz"))
        assertEquals("3+2" to TimeControlCategory.BLITZ, ChessComRepository.formatTimeControl("180+2", "blitz"))
        assertEquals("5+0" to TimeControlCategory.BLITZ, ChessComRepository.formatTimeControl("300", "blitz"))
        assertEquals("10+0" to TimeControlCategory.RAPID, ChessComRepository.formatTimeControl("600", "rapid"))
        assertEquals("15+10" to TimeControlCategory.RAPID, ChessComRepository.formatTimeControl("900+10", "rapid"))
        assertEquals("1+0" to TimeControlCategory.BULLET, ChessComRepository.formatTimeControl("60", "bullet"))

        assertEquals(PerspectiveResult.WIN, ChessComRepository.computePerspectiveResult("win", "resigned").first)
        assertEquals(PerspectiveResult.LOSS, ChessComRepository.computePerspectiveResult("checkmated", "win").first)
        assertEquals(PerspectiveResult.DRAW, ChessComRepository.computePerspectiveResult("stalemate", "stalemate").first)

        val parsedOpening = OpeningBook.parseChessComEcoUrl("https://www.chess.com/openings/Italian-Game-Giuoco-Piano...4.c3-Nf6")
        assertEquals("Italian Game Giuoco Piano", parsedOpening)
    }
}
