package com.example.chess.core

/**
 * Built-in ECO opening classifier using UCI move-sequence prefixes,
 * plus extraction helpers for PGN [Opening] and Chess.com [ECOUrl] tags.
 */
object OpeningBook {

    data class OpeningInfo(
        val eco: String,
        val name: String
    )

    private val entries: List<Pair<List<String>, OpeningInfo>> = listOf(
        // King's Pawn (1. e4)
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5a4", "g8f6", "e1g1") to OpeningInfo("C78", "Ruy Lopez: Morphy Defense"),
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "g8f6") to OpeningInfo("C65", "Ruy Lopez: Berlin Defense"),
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6") to OpeningInfo("C70", "Ruy Lopez: Morphy Defense"),
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5") to OpeningInfo("C60", "Ruy Lopez"),
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5", "c2c3") to OpeningInfo("C54", "Italian Game: Giuoco Piano"),
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5", "b2b4") to OpeningInfo("C51", "Italian Game: Evans Gambit"),
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "f3g5") to OpeningInfo("C57", "Italian Game: Two Knights Defense"),
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6") to OpeningInfo("C55", "Italian Game: Two Knights Defense"),
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5") to OpeningInfo("C50", "Italian Game: Giuoco Piano"),
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1c4") to OpeningInfo("C50", "Italian Game"),
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "d2d4", "e5d4", "f3d4") to OpeningInfo("C45", "Scotch Game"),
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "d2d4") to OpeningInfo("C44", "Scotch Game"),
        listOf("e2e4", "e7e5", "g1f3", "b8c6", "b1c3", "g8f6") to OpeningInfo("C47", "Four Knights Game"),
        listOf("e2e4", "e7e5", "g1f3", "g8f6") to OpeningInfo("C42", "Petrov's Defense"),
        listOf("e2e4", "e7e5", "g1f3", "d7d6") to OpeningInfo("C41", "Philidor Defense"),
        listOf("e2e4", "e7e5", "f2f4", "e5f4") to OpeningInfo("C33", "King's Gambit Accepted"),
        listOf("e2e4", "e7e5", "f2f4") to OpeningInfo("C30", "King's Gambit"),
        listOf("e2e4", "e7e5", "b1c3", "g8f6") to OpeningInfo("C27", "Vienna Game"),
        listOf("e2e4", "e7e5", "b1c3") to OpeningInfo("C25", "Vienna Game"),
        listOf("e2e4", "e7e5", "f1c4") to OpeningInfo("C23", "Bishop's Opening"),
        listOf("e2e4", "e7e5", "d1h5") to OpeningInfo("C20", "Wayward Queen Attack"),
        listOf("e2e4", "e7e5") to OpeningInfo("C20", "King's Pawn Game"),

        // Sicilian Defense
        listOf("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "a7a6") to OpeningInfo("B90", "Sicilian Defense: Najdorf"),
        listOf("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "g7g6") to OpeningInfo("B70", "Sicilian Defense: Dragon"),
        listOf("e2e4", "c7c5", "g1f3", "b8c6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "e7e5") to OpeningInfo("B33", "Sicilian Defense: Sveshnikov"),
        listOf("e2e4", "c7c5", "g1f3", "b8c6", "f1b5") to OpeningInfo("B30", "Sicilian Defense: Rossolimo"),
        listOf("e2e4", "c7c5", "c2c3") to OpeningInfo("B22", "Sicilian Defense: Alapin"),
        listOf("e2e4", "c7c5", "b1c3") to OpeningInfo("B23", "Sicilian Defense: Closed"),
        listOf("e2e4", "c7c5", "g1f3", "e7e6") to OpeningInfo("B40", "Sicilian Defense: French Variation"),
        listOf("e2e4", "c7c5", "g1f3", "d7d6") to OpeningInfo("B50", "Sicilian Defense"),
        listOf("e2e4", "c7c5") to OpeningInfo("B20", "Sicilian Defense"),

        // French & Caro-Kann & Semi-Open
        listOf("e2e4", "e7e6", "d2d4", "d7d5", "b1c3", "f8b4") to OpeningInfo("C15", "French Defense: Winawer"),
        listOf("e2e4", "e7e6", "d2d4", "d7d5", "e4e5") to OpeningInfo("C02", "French Defense: Advance"),
        listOf("e2e4", "e7e6", "d2d4", "d7d5", "e4d5") to OpeningInfo("C01", "French Defense: Exchange"),
        listOf("e2e4", "e7e6", "d2d4", "d7d5", "b1d2") to OpeningInfo("C03", "French Defense: Tarrasch"),
        listOf("e2e4", "e7e6") to OpeningInfo("C00", "French Defense"),
        listOf("e2e4", "c7c6", "d2d4", "d7d5", "e4e5") to OpeningInfo("B12", "Caro-Kann Defense: Advance"),
        listOf("e2e4", "c7c6", "d2d4", "d7d5", "b1c3") to OpeningInfo("B15", "Caro-Kann Defense: Classical"),
        listOf("e2e4", "c7c6", "d2d4", "d7d5", "e4d5") to OpeningInfo("B13", "Caro-Kann Defense: Exchange"),
        listOf("e2e4", "c7c6") to OpeningInfo("B10", "Caro-Kann Defense"),
        listOf("e2e4", "d7d5", "e4d5", "d8d5") to OpeningInfo("B01", "Scandinavian Defense"),
        listOf("e2e4", "d7d5") to OpeningInfo("B01", "Scandinavian Defense"),
        listOf("e2e4", "d7d6", "d2d4", "g8f6", "b1c3", "g7g6") to OpeningInfo("B07", "Pirc Defense"),
        listOf("e2e4", "g8f6") to OpeningInfo("B02", "Alekhine's Defense"),
        listOf("e2e4", "g7g6") to OpeningInfo("B06", "Modern Defense"),

        // Queen's Pawn (1. d4)
        listOf("d2d4", "d7d5", "c2c4", "e7e6") to OpeningInfo("D30", "Queen's Gambit Declined"),
        listOf("d2d4", "d7d5", "c2c4", "c7c6") to OpeningInfo("D10", "Slav Defense"),
        listOf("d2d4", "d7d5", "c2c4", "d5c4") to OpeningInfo("D20", "Queen's Gambit Accepted"),
        listOf("d2d4", "d7d5", "c2c4") to OpeningInfo("D06", "Queen's Gambit"),
        listOf("d2d4", "d7d5", "c1f4") to OpeningInfo("D02", "London System"),
        listOf("d2d4", "g8f6", "c1f4") to OpeningInfo("A45", "London System"),
        listOf("d2d4", "g8f6", "g1f3", "d7d5", "c1f4") to OpeningInfo("D02", "London System"),
        listOf("d2d4", "g8f6", "c2c4", "e7e6", "b1c3", "f8b4") to OpeningInfo("E20", "Nimzo-Indian Defense"),
        listOf("d2d4", "g8f6", "c2c4", "e7e6", "g1f3", "b7b6") to OpeningInfo("E12", "Queen's Indian Defense"),
        listOf("d2d4", "g8f6", "c2c4", "g7g6", "b1c3", "d7d5") to OpeningInfo("D80", "Grünfeld Defense"),
        listOf("d2d4", "g8f6", "c2c4", "g7g6", "b1c3", "f8g7") to OpeningInfo("E60", "King's Indian Defense"),
        listOf("d2d4", "g8f6", "c2c4", "g7g6") to OpeningInfo("E60", "King's Indian Defense"),
        listOf("d2d4", "g8f6", "c2c4", "c7c5", "d4d5") to OpeningInfo("A60", "Benoni Defense"),
        listOf("d2d4", "g8f6", "c2c4", "e7e6", "g2g3") to OpeningInfo("E01", "Catalan Opening"),
        listOf("d2d4", "f7f5") to OpeningInfo("A80", "Dutch Defense"),
        listOf("d2d4", "d7d5") to OpeningInfo("D00", "Queen's Pawn Game"),
        listOf("d2d4", "g8f6") to OpeningInfo("A45", "Indian Defense"),

        // Flank Openings
        listOf("c2c4", "e7e5") to OpeningInfo("A20", "English Opening: Reversed Sicilian"),
        listOf("c2c4", "c7c5") to OpeningInfo("A30", "English Opening: Symmetrical"),
        listOf("c2c4") to OpeningInfo("A10", "English Opening"),
        listOf("g1f3", "d7d5", "c2c4") to OpeningInfo("A09", "Réti Opening"),
        listOf("g1f3", "d7d5") to OpeningInfo("A06", "Réti Opening"),
        listOf("g1f3") to OpeningInfo("A04", "Zukertort Opening"),
        listOf("f2f4") to OpeningInfo("A02", "Bird's Opening"),
        listOf("b2b3") to OpeningInfo("A01", "Nimzowitsch-Larsen Attack")
    ).sortedByDescending { it.first.size }

    fun detectFromUciMoves(uciMoves: List<String>): OpeningInfo? {
        if (uciMoves.isEmpty()) return null
        for ((prefix, info) in entries) {
            if (uciMoves.size >= prefix.size && uciMoves.subList(0, prefix.size) == prefix) {
                return info
            }
        }
        return null
    }

    fun extractFromHeaders(headers: Map<String, String>, uciMoves: List<String>): String? {
        headers["Opening"]?.takeIf { it.isNotBlank() && it != "?" }?.let { return it }
        headers["ECOUrl"]?.takeIf { it.isNotBlank() }?.let { url ->
            parseChessComEcoUrl(url)?.let { return it }
        }
        val book = detectFromUciMoves(uciMoves)
        val eco = headers["ECO"]?.takeIf { it.isNotBlank() && it != "?" } ?: book?.eco
        return when {
            book != null && eco != null -> "$eco ${book.name}"
            book != null -> book.name
            eco != null -> "ECO $eco"
            else -> null
        }
    }

    fun parseChessComEcoUrl(ecoUrl: String): String? {
        val marker = "/openings/"
        val idx = ecoUrl.indexOf(marker)
        if (idx < 0) return null
        val slug = ecoUrl.substring(idx + marker.length).trim('/')
        if (slug.isEmpty()) return null
        // Remove trailing move notation fragments like "...4.Nf3-Nf6"
        val cleanSlug = slug.split("...")[0].trim('-')
        val words = cleanSlug.split('-')
            .filter { it.isNotBlank() && !it.matches(Regex("^\\d+\\..*")) }
        if (words.isEmpty()) return null
        return words.joinToString(" ") { w ->
            if (w.length <= 2) w else w.replaceFirstChar { it.uppercaseChar() }
        }
    }
}
