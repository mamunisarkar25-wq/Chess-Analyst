package com.example.chess.engine

import android.content.Context
import android.util.Log
import com.example.chess.core.ChessBoardState
import com.example.chess.core.FenValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dual-mode local tactical engine:
 * 1. If a bundled native Stockfish binary (`libstockfish.so`) exists in the app's
 *    `nativeLibraryDir`, speaks full UCI protocol with conservative thread/hash limits.
 * 2. Otherwise seamlessly runs [LocalTacticalEngine] (PeSTO Multi-PV Alpha-Beta) locally
 *    with zero external binary downloads (100% Google Play DCL compliant).
 */
class StockfishUciEngine(private val context: Context? = null) {

    private val mutex = Mutex()
    private val localEngine = LocalTacticalEngine()

    private var uciProcess: Process? = null
    private var uciWriter: BufferedWriter? = null
    private var uciReader: BufferedReader? = null
    private var nativeStockfishReady = false
    private var initialized = false
    private val cancelled = AtomicBoolean(false)

    val isUsingNativeStockfish: Boolean get() = nativeStockfishReady
    val activeEngineLabel: String
        get() = if (nativeStockfishReady) "Stockfish UCI (Native)" else "Stockfish-Compatible Local Engine (PeSTO)"

    private fun ensureInitialized(threads: Int, hashMb: Int) {
        if (initialized) return
        initialized = true
        val nativeDir = context?.applicationInfo?.nativeLibraryDir ?: return
        val candidate = File(nativeDir, "libstockfish.so")
        if (!candidate.exists() || !candidate.canExecute()) return

        try {
            val process = ProcessBuilder(candidate.absolutePath)
                .redirectErrorStream(true)
                .start()
            val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            fun send(cmd: String) {
                writer.write("$cmd\n")
                writer.flush()
            }

            send("uci")
            var gotUciOk = false
            val deadline = System.currentTimeMillis() + 3000L
            while (System.currentTimeMillis() < deadline) {
                val line = reader.readLine() ?: break
                if (line.trim() == "uciok") {
                    gotUciOk = true
                    break
                }
            }
            if (!gotUciOk) {
                process.destroy()
                return
            }

            val safeThreads = threads.coerceIn(1, (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1).coerceAtMost(2))
            val safeHash = hashMb.coerceIn(16, 64)
            send("setoption name Threads value $safeThreads")
            send("setoption name Hash value $safeHash")
            send("isready")
            while (System.currentTimeMillis() < deadline + 2000L) {
                val line = reader.readLine() ?: break
                if (line.trim() == "readyok") {
                    uciProcess = process
                    uciWriter = writer
                    uciReader = reader
                    nativeStockfishReady = true
                    break
                }
            }
            if (!nativeStockfishReady) {
                process.destroy()
            }
        } catch (e: Exception) {
            Log.w("StockfishUciEngine", "Native UCI start skipped, using local tactical engine: ${e.message}")
            shutdownNative()
        }
    }

    fun cancelCurrentAnalysis() {
        cancelled.set(true)
        localEngine.cancel()
        try {
            uciWriter?.apply {
                write("stop\n")
                flush()
            }
        } catch (_: Exception) {
        }
    }

    fun clearMemory() {
        localEngine.clearHash()
        if (nativeStockfishReady) {
            try {
                uciWriter?.apply {
                    write("ucinewgame\n")
                    flush()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun shutdown() {
        cancelCurrentAnalysis()
        shutdownNative()
        localEngine.clearHash()
    }

    private fun shutdownNative() {
        nativeStockfishReady = false
        try { uciWriter?.write("quit\n"); uciWriter?.flush() } catch (_: Exception) {}
        try { uciWriter?.close() } catch (_: Exception) {}
        try { uciReader?.close() } catch (_: Exception) {}
        try { uciProcess?.destroy() } catch (_: Exception) {}
        uciWriter = null
        uciReader = null
        uciProcess = null
    }

    suspend fun evaluatePosition(
        fen: String,
        depth: Int = 5,
        timeLimitMs: Long = 650L,
        multiPv: Int = 3,
        threads: Int = 1,
        hashMb: Int = 32
    ): EngineEvaluation = withContext(Dispatchers.Default) {
        mutex.withLock {
            cancelled.set(false)
            ensureInitialized(threads, hashMb)

            val parsed = ChessBoardState.fromFen(fen)
            val board = when (parsed) {
                is FenValidationResult.Valid -> parsed.state
                is FenValidationResult.Invalid -> {
                    return@withLock EngineEvaluation(
                        fen = fen,
                        score = EvalScore(0),
                        bestMoveUci = null,
                        bestMoveSan = null,
                        engineName = activeEngineLabel
                    )
                }
            }

            if (nativeStockfishReady) {
                val nativeResult = runNativeUciEvaluation(board, fen, depth.coerceIn(6, 16), timeLimitMs, multiPv)
                if (nativeResult != null) return@withLock nativeResult
            }

            // Local PeSTO Multi-PV engine
            val localDepth = depth.coerceIn(3, 6)
            localEngine.analyzePosition(
                board = board,
                maxDepth = localDepth,
                timeLimitMs = timeLimitMs,
                multiPv = multiPv
            )
        }
    }

    private fun runNativeUciEvaluation(
        board: ChessBoardState,
        fen: String,
        depth: Int,
        timeLimitMs: Long,
        multiPv: Int
    ): EngineEvaluation? {
        val writer = uciWriter ?: return null
        val reader = uciReader ?: return null
        return try {
            writer.write("setoption name MultiPV value $multiPv\n")
            writer.write("position fen $fen\n")
            writer.write("go depth $depth movetime $timeLimitMs\n")
            writer.flush()

            val pvMap = linkedMapOf<Int, PvLine>()
            var lastDepth = 0
            var lastNodes = 0L
            var bestMoveUci: String? = null

            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("info ") && line.contains(" score ") && line.contains(" pv ")) {
                    parseUciInfoLine(line, board)?.let { (pvLine, nodes) ->
                        pvMap[pvLine.multiPvIndex] = pvLine
                        lastDepth = maxOf(lastDepth, pvLine.depth)
                        lastNodes = maxOf(lastNodes, nodes)
                    }
                } else if (line.startsWith("bestmove ")) {
                    bestMoveUci = line.split(" ").getOrNull(1)?.takeIf { it != "(none)" }
                    break
                }
            }

            val lines = pvMap.values.sortedBy { it.multiPvIndex }
            val top = lines.firstOrNull()
            val bestSan = bestMoveUci?.let { uci ->
                board.generateLegalMoves().firstOrNull { it.toUci() == uci }?.let { board.moveToSan(it) }
            } ?: top?.sanMoves?.firstOrNull()

            EngineEvaluation(
                fen = fen,
                score = top?.score ?: EvalScore(0),
                bestMoveUci = bestMoveUci ?: top?.uciMoves?.firstOrNull(),
                bestMoveSan = bestSan,
                secondBestScore = lines.getOrNull(1)?.score,
                pvLines = lines,
                depth = lastDepth,
                nodes = lastNodes,
                engineName = "Stockfish UCI"
            )
        } catch (e: Exception) {
            Log.e("StockfishUciEngine", "UCI read failure, switching to local tactical engine", e)
            shutdownNative()
            null
        }
    }

    private fun parseUciInfoLine(line: String, board: ChessBoardState): Pair<PvLine, Long>? {
        val tokens = line.split(Regex("\\s+"))
        var depth = 0
        var multiPv = 1
        var nodes = 0L
        var cp: Int? = null
        var mate: Int? = null
        var pvStart = -1

        var i = 0
        while (i < tokens.size) {
            when (tokens[i]) {
                "depth" -> depth = tokens.getOrNull(i + 1)?.toIntOrNull() ?: depth
                "multipv" -> multiPv = tokens.getOrNull(i + 1)?.toIntOrNull() ?: multiPv
                "nodes" -> nodes = tokens.getOrNull(i + 1)?.toLongOrNull() ?: nodes
                "score" -> {
                    val kind = tokens.getOrNull(i + 1)
                    val value = tokens.getOrNull(i + 2)?.toIntOrNull()
                    if (kind == "cp" && value != null) cp = value
                    else if (kind == "mate" && value != null) mate = value
                }
                "pv" -> {
                    pvStart = i + 1
                    break
                }
            }
            i++
        }
        if (pvStart < 0 || pvStart >= tokens.size) return null
        val ucis = tokens.subList(pvStart, tokens.size).take(8)
        val sans = mutableListOf<String>()
        var cur = board
        for (uci in ucis) {
            val legal = cur.generateLegalMoves()
            val m = legal.firstOrNull { it.toUci() == uci } ?: break
            sans.add(cur.moveToSan(m, legal))
            cur = cur.applyMoveUnchecked(m)
        }
        val eval = EvalScore.fromSideToMove(cp ?: 0, mate, board.sideToMove)
        return PvLine(multiPv, eval, ucis, sans, depth) to nodes
    }
}
