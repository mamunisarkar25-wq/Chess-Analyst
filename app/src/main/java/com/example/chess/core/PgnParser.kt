package com.example.chess.core

import com.example.chess.analysis.AnalyzedMove
import com.example.chess.analysis.GameReviewReport

data class ParsedMoveNode(
    val plyIndex: Int, // 1-based ply index
    val moveNumber: Int,
    val side: PieceColor,
    val move: ChessMove,
    val san: String,
    val uci: String,
    val fenBefore: String,
    val fenAfter: String
)

data class ParsedPgnGame(
    val id: String,
    val headers: Map<String, String>,
    val initialFen: String,
    val moves: List<ParsedMoveNode>,
    val result: String,
    val openingName: String?,
    val warning: String? = null
) {
    val whitePlayer: String get() = headers["White"]?.takeIf { it.isNotBlank() } ?: "White"
    val blackPlayer: String get() = headers["Black"]?.takeIf { it.isNotBlank() } ?: "Black"
    val whiteElo: Int? get() = headers["WhiteElo"]?.toIntOrNull()
    val blackElo: Int? get() = headers["BlackElo"]?.toIntOrNull()
    val event: String get() = headers["Event"]?.takeIf { it.isNotBlank() && it != "?" } ?: "Imported Game"
    val date: String get() = headers["Date"]?.takeIf { it.isNotBlank() && it != "????.??.??" } ?: ""
    val timeControl: String? get() = headers["TimeControl"]?.takeIf { it.isNotBlank() && it != "?" }
}

sealed class PgnParseResult {
    data class Success(val games: List<ParsedPgnGame>, val warnings: List<String> = emptyList()) : PgnParseResult()
    data class Error(val message: String) : PgnParseResult()
}

object PgnParser {

    private val HEADER_REGEX = Regex("""^\s*\[(\w+)\s+"(.*)"\]\s*$""")
    private val RESULT_TOKENS = setOf("1-0", "0-1", "1/2-1/2", "*")

    fun parseMultiple(rawPgn: String): PgnParseResult {
        val normalized = rawPgn.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (normalized.isEmpty()) {
            return PgnParseResult.Error("PGN input is empty. Paste PGN notation or open a .pgn file.")
        }

        val rawBlocks = splitIntoGameBlocks(normalized)
        if (rawBlocks.isEmpty()) {
            return PgnParseResult.Error("No valid chess games were found in the PGN input.")
        }

        val parsedGames = mutableListOf<ParsedPgnGame>()
        val warnings = mutableListOf<String>()

        rawBlocks.forEachIndexed { idx, block ->
            when (val single = parseSingleBlock(block, idx)) {
                is SingleBlockResult.Parsed -> {
                    parsedGames.add(single.game)
                    single.game.warning?.let { warnings.add("Game ${idx + 1}: $it") }
                }
                is SingleBlockResult.Failed -> {
                    warnings.add("Game ${idx + 1} skipped: ${single.reason}")
                }
            }
        }

        return if (parsedGames.isEmpty()) {
            PgnParseResult.Error(
                warnings.firstOrNull() ?: "Could not parse any valid chess moves from the PGN."
            )
        } else {
            PgnParseResult.Success(parsedGames, warnings)
        }
    }

    private sealed class SingleBlockResult {
        data class Parsed(val game: ParsedPgnGame) : SingleBlockResult()
        data class Failed(val reason: String) : SingleBlockResult()
    }

    private fun splitIntoGameBlocks(text: String): List<String> {
        val lines = text.split('\n')
        val blocks = mutableListOf<String>()
        val current = StringBuilder()
        var sawMovesInCurrent = false

        for (line in lines) {
            val trimmed = line.trim()
            val isHeader = HEADER_REGEX.matches(trimmed)
            if (isHeader && sawMovesInCurrent && current.isNotBlank()) {
                blocks.add(current.toString().trim())
                current.clear()
                sawMovesInCurrent = false
            }
            if (trimmed.isNotEmpty() && !isHeader && !trimmed.startsWith("%")) {
                sawMovesInCurrent = true
            }
            current.appendLine(line)
        }
        if (current.isNotBlank()) {
            blocks.add(current.toString().trim())
        }
        return blocks
    }

    private fun parseSingleBlock(block: String, index: Int): SingleBlockResult {
        val headers = linkedMapOf<String, String>()
        val moveTextBuilder = StringBuilder()

        for (line in block.split('\n')) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("%") || trimmed.startsWith(";")) continue
            val match = HEADER_REGEX.matchEntire(trimmed)
            if (match != null) {
                headers[match.groupValues[1]] = match.groupValues[2].replace("\\\"", "\"")
            } else {
                moveTextBuilder.append(' ').append(trimmed)
            }
        }

        val initialFen = headers["FEN"]?.takeIf { it.isNotBlank() } ?: ChessBoardState.STARTING_FEN
        val initialValidation = ChessBoardState.fromFen(initialFen)
        var board = when (initialValidation) {
            is FenValidationResult.Valid -> initialValidation.state
            is FenValidationResult.Invalid -> {
                return SingleBlockResult.Failed("Invalid [FEN] header: ${initialValidation.reason}")
            }
        }

        val cleanedMovetext = stripCommentsAndVariations(moveTextBuilder.toString())
        val tokens = tokenizeMoveText(cleanedMovetext)

        val moves = mutableListOf<ParsedMoveNode>()
        var parsedResult = headers["Result"]?.takeIf { it in RESULT_TOKENS } ?: "*"
        var warning: String? = null

        for (token in tokens) {
            if (token in RESULT_TOKENS) {
                parsedResult = token
                break
            }
            if (token.startsWith("$")) continue // NAG annotation
            if (token.matches(Regex("""^\d+\.+$"""))) continue // Move number like "1." or "12..."

            // Strip inline move number prefix like "1.e4" or "12...Nf6"
            val sanCandidate = token.replace(Regex("""^\d+\.+"""), "").trim()
            if (sanCandidate.isEmpty() || sanCandidate in RESULT_TOKENS) {
                if (sanCandidate in RESULT_TOKENS) parsedResult = sanCandidate
                continue
            }

            val legalMove = board.parseSanMove(sanCandidate)
            if (legalMove == null) {
                warning = "Stopped at unrecognized or illegal move '$sanCandidate' (ply ${moves.size + 1}). Partial game loaded."
                break
            }
            val fenBefore = board.toFen()
            val canonicalSan = board.moveToSan(legalMove)
            val moveNumber = board.fullMoveNumber
            val side = board.sideToMove
            board = board.applyMoveUnchecked(legalMove)
            moves.add(
                ParsedMoveNode(
                    plyIndex = moves.size + 1,
                    moveNumber = moveNumber,
                    side = side,
                    move = legalMove,
                    san = canonicalSan,
                    uci = legalMove.toUci(),
                    fenBefore = fenBefore,
                    fenAfter = board.toFen()
                )
            )
        }

        if (moves.isEmpty() && headers["FEN"].isNullOrBlank()) {
            return SingleBlockResult.Failed("No valid moves found.")
        }

        val opening = OpeningBook.extractFromHeaders(headers, moves.map { it.uci })
        return SingleBlockResult.Parsed(
            ParsedPgnGame(
                id = "pgn_${index}_${System.nanoTime()}",
                headers = headers,
                initialFen = initialFen,
                moves = moves,
                result = parsedResult,
                openingName = opening,
                warning = warning
            )
        )
    }

    private fun stripCommentsAndVariations(raw: String): String {
        val sb = StringBuilder(raw.length)
        var braceDepth = 0
        var parenDepth = 0
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '{' && parenDepth == 0 -> braceDepth++
                c == '}' && braceDepth > 0 -> braceDepth--
                c == '(' && braceDepth == 0 -> parenDepth++
                c == ')' && parenDepth > 0 -> parenDepth--
                braceDepth == 0 && parenDepth == 0 -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun tokenizeMoveText(text: String): List<String> {
        return text.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
    }

    fun exportAnnotatedPgn(report: GameReviewReport): String {
        val sb = StringBuilder()
        val game = report.game
        val headers = linkedMapOf(
            "Event" to game.event,
            "Site" to (game.headers["Site"] ?: "Chess Analyst"),
            "Date" to (game.date.ifBlank { "????.??.??" }),
            "White" to game.whitePlayer,
            "Black" to game.blackPlayer,
            "Result" to game.result
        )
        game.whiteElo?.let { headers["WhiteElo"] = it.toString() }
        game.blackElo?.let { headers["BlackElo"] = it.toString() }
        game.openingName?.let { headers["Opening"] = it }
        if (game.initialFen != ChessBoardState.STARTING_FEN) {
            headers["SetUp"] = "1"
            headers["FEN"] = game.initialFen
        }
        headers["Annotator"] = "Chess Analyst (${report.engineName})"

        for ((k, v) in headers) {
            sb.appendLine("[$k \"${v.replace("\"", "\\\"")}\"]")
        }
        sb.appendLine()
        sb.appendLine(
            "{ White Accuracy: ${"%.1f".format(report.whiteAccuracy)}% (Est. ${report.whiteEstimatedElo}) | " +
                "Black Accuracy: ${"%.1f".format(report.blackAccuracy)}% (Est. ${report.blackEstimatedElo}) }"
        )
        sb.appendLine()

        report.moves.forEachIndexed { idx, am: AnalyzedMove ->
            if (am.node.side == PieceColor.WHITE) {
                sb.append("${am.node.moveNumber}. ")
            } else if (idx == 0) {
                sb.append("${am.node.moveNumber}... ")
            }
            sb.append(am.node.san)
            if (am.classification.nag.isNotEmpty()) {
                sb.append(' ').append(am.classification.nag)
            }
            sb.append(" { [${am.classification.label}] ${am.evalAfterDisplay}")
            if (am.bestAlternativeSan != null && am.bestAlternativeSan != am.node.san) {
                sb.append(" | Best: ${am.bestAlternativeSan}")
            }
            sb.append(" } ")
        }
        sb.append(game.result)
        return sb.toString()
    }
}
