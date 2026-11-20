package com.example.data.chesscom

import com.example.chess.core.OpeningBook
import com.example.chess.core.ParsedPgnGame
import com.example.chess.core.PgnParseResult
import com.example.chess.core.PgnParser
import com.example.chess.core.PieceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PerspectiveResult(val label: String) {
    WIN("WIN"),
    LOSS("LOSS"),
    DRAW("DRAW")
}

enum class TimeControlCategory(val label: String) {
    BULLET("Bullet"),
    BLITZ("Blitz"),
    RAPID("Rapid"),
    CLASSICAL("Classical"),
    CUSTOM("Custom")
}

data class ChessComPlayerSummary(
    val username: String,
    val displayName: String?,
    val title: String?,
    val avatarUrl: String?,
    val bulletRating: Int?,
    val blitzRating: Int?,
    val rapidRating: Int?
)

data class ChessComRecentGame(
    val id: String,
    val url: String,
    val playerSide: PieceColor,
    val playerUsername: String,
    val opponentUsername: String,
    val playerRating: Int?,
    val opponentRating: Int?,
    val timeControlDisplay: String,
    val timeCategory: TimeControlCategory,
    val endTimeSeconds: Long,
    val formattedDate: String,
    val result: PerspectiveResult,
    val resultReason: String,
    val openingName: String?,
    val rawPgn: String,
    val parsedGame: ParsedPgnGame?
)

sealed class ChessComFetchResult {
    data class Success(
        val player: ChessComPlayerSummary,
        val games: List<ChessComRecentGame>
    ) : ChessComFetchResult()

    data class Error(val message: String, val isRateLimited: Boolean = false) : ChessComFetchResult()
}

class ChessComRepository(
    private val api: ChessComApi = ChessComApi.create()
) {
    private val usernamePattern = Regex("^[A-Za-z0-9_-]{3,25}$")

    fun validateUsernameFormat(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "Please enter a public Chess.com username."
        if (trimmed.length < 3) return "Chess.com usernames must be at least 3 characters."
        if (!usernamePattern.matches(trimmed)) {
            return "Username can only contain letters, numbers, underscores, and hyphens."
        }
        return null
    }

    /**
     * Fetches public profile, stats, and latest 10 standard chess games by iterating newest
     * monthly archives first and stopping as soon as 10 valid games are gathered.
     */
    suspend fun fetchLatestGames(rawUsername: String, maxGames: Int = 10): ChessComFetchResult =
        withContext(Dispatchers.IO) {
            val formatError = validateUsernameFormat(rawUsername)
            if (formatError != null) return@withContext ChessComFetchResult.Error(formatError)

            val normalized = rawUsername.trim().lowercase(Locale.US)

            try {
                // 1. Fetch public profile
                val profileResp = api.getPlayerProfile(normalized)
                if (!profileResp.isSuccessful) {
                    return@withContext mapHttpError(profileResp.code(), rawUsername.trim())
                }
                val profileDto = profileResp.body()
                    ?: return@withContext ChessComFetchResult.Error("Empty player profile returned from Chess.com.")

                if (profileDto.status?.startsWith("closed", ignoreCase = true) == true) {
                    return@withContext ChessComFetchResult.Error("This Chess.com account is closed or unavailable.")
                }

                // 2. Fetch optional ratings summary (non-fatal if unavailable)
                val statsDto = try {
                    val s = api.getPlayerStats(normalized)
                    if (s.isSuccessful) s.body() else null
                } catch (_: Exception) {
                    null
                }

                val playerSummary = ChessComPlayerSummary(
                    username = profileDto.username.ifBlank { rawUsername.trim() },
                    displayName = profileDto.name?.takeIf { it.isNotBlank() },
                    title = profileDto.title?.takeIf { it.isNotBlank() },
                    avatarUrl = profileDto.avatar?.takeIf { it.isNotBlank() },
                    bulletRating = statsDto?.bullet?.last?.rating,
                    blitzRating = statsDto?.blitz?.last?.rating,
                    rapidRating = statsDto?.rapid?.last?.rating
                )

                // 3. Fetch monthly archive list
                val archivesResp = api.getGameArchives(normalized)
                if (!archivesResp.isSuccessful) {
                    return@withContext mapHttpError(archivesResp.code(), playerSummary.username)
                }
                val archives = archivesResp.body()?.archives.orEmpty()
                if (archives.isEmpty()) {
                    return@withContext ChessComFetchResult.Success(playerSummary, emptyList())
                }

                // 4. Paginate newest archives backwards, stopping once we have 10 games (cap at 4 months checked)
                val collected = mutableListOf<ChessComRecentGame>()
                val newestArchives = archives.asReversed().take(6)

                for (archiveUrl in newestArchives) {
                    val monthResp = api.getGamesFromArchiveUrl(archiveUrl)
                    if (monthResp.code() == 429) {
                        if (collected.isNotEmpty()) break
                        return@withContext ChessComFetchResult.Error(
                            "Chess.com API rate limit reached. Please wait a moment and tap Refresh.",
                            isRateLimited = true
                        )
                    }
                    if (!monthResp.isSuccessful) continue

                    val monthGames = monthResp.body()?.games.orEmpty()
                        .filter { (it.rules ?: "chess").equals("chess", ignoreCase = true) && !it.pgn.isNullOrBlank() }
                        .sortedByDescending { it.endTime ?: 0L }

                    for (dto in monthGames) {
                        val mapped = mapGameDto(dto, normalized) ?: continue
                        collected.add(mapped)
                        if (collected.size >= maxGames) break
                    }
                    if (collected.size >= maxGames) break
                }

                val sortedLatest = collected
                    .sortedByDescending { it.endTimeSeconds }
                    .take(maxGames)

                ChessComFetchResult.Success(playerSummary, sortedLatest)
            } catch (e: IOException) {
                ChessComFetchResult.Error("Network error while reaching Chess.com. Please check your internet connection.")
            } catch (e: Exception) {
                ChessComFetchResult.Error("Could not load Chess.com games: ${e.localizedMessage ?: "Unexpected error"}")
            }
        }

    private fun mapHttpError(code: Int, username: String): ChessComFetchResult.Error = when (code) {
        404 -> ChessComFetchResult.Error("Chess.com user '$username' was not found. Please check the spelling.")
        410 -> ChessComFetchResult.Error("This profile or archive is private or no longer available on Chess.com.")
        429 -> ChessComFetchResult.Error("Chess.com API rate limit reached. Wait a few seconds and try again.", isRateLimited = true)
        in 500..599 -> ChessComFetchResult.Error("Chess.com servers are temporarily unavailable (HTTP $code).")
        else -> ChessComFetchResult.Error("Chess.com request failed (HTTP $code).")
    }

    internal fun mapGameDto(dto: ChessComGameDto, connectedUserLower: String): ChessComRecentGame? {
        val rawPgn = dto.pgn ?: return null
        val white = dto.white ?: return null
        val black = dto.black ?: return null

        val isWhite = white.username.equals(connectedUserLower, ignoreCase = true)
        val isBlack = black.username.equals(connectedUserLower, ignoreCase = true)
        val playerSide = when {
            isWhite -> PieceColor.WHITE
            isBlack -> PieceColor.BLACK
            else -> PieceColor.WHITE
        }

        val playerDto = if (playerSide == PieceColor.WHITE) white else black
        val opponentDto = if (playerSide == PieceColor.WHITE) black else white

        val (result, reason) = computePerspectiveResult(playerDto.result, opponentDto.result)
        val (tcDisplay, tcCategory) = formatTimeControl(dto.timeControl, dto.timeClass)
        val endSec = dto.endTime ?: 0L
        val formattedDate = if (endSec > 0L) {
            SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(endSec * 1000L))
        } else {
            ""
        }

        val parsed = (PgnParser.parseMultiple(rawPgn) as? PgnParseResult.Success)?.games?.firstOrNull()
        val opening = parsed?.openingName ?: extractOpeningFromRawPgn(rawPgn)

        return ChessComRecentGame(
            id = dto.url.ifBlank { "${endSec}_${opponentDto.username}" },
            url = dto.url,
            playerSide = playerSide,
            playerUsername = playerDto.username,
            opponentUsername = opponentDto.username.ifBlank { "Unknown" },
            playerRating = playerDto.rating,
            opponentRating = opponentDto.rating,
            timeControlDisplay = tcDisplay,
            timeCategory = tcCategory,
            endTimeSeconds = endSec,
            formattedDate = formattedDate,
            result = result,
            resultReason = reason,
            openingName = opening,
            rawPgn = rawPgn,
            parsedGame = parsed
        )
    }

    private fun extractOpeningFromRawPgn(pgn: String): String? {
        val ecoUrlRegex = Regex("""\[ECOUrl\s+"([^"]+)"\]""")
        ecoUrlRegex.find(pgn)?.groupValues?.getOrNull(1)?.let { url ->
            OpeningBook.parseChessComEcoUrl(url)?.let { return it }
        }
        val openingRegex = Regex("""\[Opening\s+"([^"]+)"\]""")
        return openingRegex.find(pgn)?.groupValues?.getOrNull(1)
    }

    companion object {
        private val DRAW_CODES = setOf(
            "agreed", "repetition", "stalemate", "insufficient",
            "50move", "timevsinsufficient", "draw"
        )

        fun computePerspectiveResult(playerCode: String, opponentCode: String): Pair<PerspectiveResult, String> {
            val p = playerCode.lowercase(Locale.US)
            val o = opponentCode.lowercase(Locale.US)
            return when {
                p == "win" -> PerspectiveResult.WIN to humanizeResultCode(o, won = true)
                p in DRAW_CODES || o in DRAW_CODES -> PerspectiveResult.DRAW to humanizeResultCode(p, won = false)
                else -> PerspectiveResult.LOSS to humanizeResultCode(p, won = false)
            }
        }

        private fun humanizeResultCode(code: String, won: Boolean): String = when (code) {
            "checkmated" -> if (won) "By checkmate" else "Checkmated"
            "resigned" -> if (won) "Opponent resigned" else "Resigned"
            "timeout" -> if (won) "Won on time" else "Lost on time"
            "abandoned" -> if (won) "Opponent abandoned" else "Abandoned"
            "agreed" -> "Draw by agreement"
            "stalemate" -> "Stalemate"
            "repetition" -> "Threefold repetition"
            "insufficient" -> "Insufficient material"
            "50move" -> "50-move rule"
            "timevsinsufficient" -> "Timeout vs insufficient"
            else -> if (won) "Won" else "Finished"
        }

        /**
         * Formats Chess.com `time_control` (e.g. "180", "180+2", "300", "600", "900+10", "1/86400")
         * into clean display notation ("3+0", "3+2", "5+0", "10+0", "15+10") and category.
         */
        fun formatTimeControl(rawTc: String?, rawTimeClass: String?): Pair<String, TimeControlCategory> {
            if (rawTc.isNullOrBlank()) {
                return "Custom" to mapTimeClass(rawTimeClass, null)
            }

            // Daily chess format: "1/86400" or "1/259200"
            if (rawTc.contains("/")) {
                val secs = rawTc.substringAfter('/').toIntOrNull()
                val days = if (secs != null && secs >= 86400) secs / 86400 else 1
                return "${days}d" to TimeControlCategory.CLASSICAL
            }

            val parts = rawTc.split("+")
            val baseSec = parts[0].toIntOrNull() ?: return rawTc to TimeControlCategory.CUSTOM
            val incSec = parts.getOrNull(1)?.toIntOrNull() ?: 0

            val display = if (baseSec >= 60 && baseSec % 60 == 0) {
                "${baseSec / 60}+$incSec"
            } else if (baseSec < 60) {
                "${baseSec}s+$incSec"
            } else {
                val mins = baseSec / 60
                val rem = baseSec % 60
                "${mins}m${rem}s+$incSec"
            }

            // Estimated 40-move game duration in seconds for accurate category classification
            val estimatedSeconds = baseSec + incSec * 40
            val category = mapTimeClass(rawTimeClass, estimatedSeconds)
            return display to category
        }

        private fun mapTimeClass(rawTimeClass: String?, estimatedSeconds: Int?): TimeControlCategory {
            return when (rawTimeClass?.lowercase(Locale.US)) {
                "bullet" -> TimeControlCategory.BULLET
                "blitz" -> TimeControlCategory.BLITZ
                "rapid" -> {
                    if (estimatedSeconds != null && estimatedSeconds >= 1800) TimeControlCategory.CLASSICAL
                    else TimeControlCategory.RAPID
                }
                "daily", "classical" -> TimeControlCategory.CLASSICAL
                else -> when {
                    estimatedSeconds == null -> TimeControlCategory.CUSTOM
                    estimatedSeconds < 180 -> TimeControlCategory.BULLET
                    estimatedSeconds < 600 -> TimeControlCategory.BLITZ
                    estimatedSeconds < 1800 -> TimeControlCategory.RAPID
                    else -> TimeControlCategory.CLASSICAL
                }
            }
        }
    }
}
