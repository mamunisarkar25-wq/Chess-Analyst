package com.example.chess.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.example.chess.core.ChessBoardState
import com.example.chess.core.ChessMove
import com.example.chess.core.FenValidationResult
import com.example.chess.core.PieceColor
import com.example.chess.core.PieceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.exp

sealed class MaiaDownloadState {
    data object Idle : MaiaDownloadState()
    data class Downloading(val model: MaiaModelSpec, val progress: Float, val downloadedMb: Float, val totalMb: Float) : MaiaDownloadState()
    data class Error(val model: MaiaModelSpec, val message: String) : MaiaDownloadState()
}

/**
 * Local Maia-3 ONNX Runtime inference engine & optional model downloader.
 * - Uses ONNX Runtime Android (`ai.onnxruntime`).
 * - Downloads optional FP16 ONNX weights from Hugging Face (`bqrio/maia3-onnx`) to app internal files.
 * - Loads sessions lazily with conservative CPU threads to protect mid-range devices.
 * - Never loads the 79M model by default.
 * - Releases native ONNX session memory cleanly on `releaseMemory()` / low memory.
 */
class Maia3Engine(private val context: Context) {

    private val modelDir: File by lazy {
        File(context.filesDir, "maia3_models").apply { mkdirs() }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val sessionMutex = Mutex()
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var loadedModel: MaiaModelSpec? = null

    private val _downloadState = MutableStateFlow<MaiaDownloadState>(MaiaDownloadState.Idle)
    val downloadState: StateFlow<MaiaDownloadState> = _downloadState.asStateFlow()

    private val _installedModels = MutableStateFlow<Set<MaiaModelSpec>>(scanInstalledModels())
    val installedModels: StateFlow<Set<MaiaModelSpec>> = _installedModels.asStateFlow()

    fun modelFile(spec: MaiaModelSpec): File = File(modelDir, spec.fileName)

    fun isModelInstalled(spec: MaiaModelSpec): Boolean {
        val f = modelFile(spec)
        return f.exists() && f.length() > 100_000L
    }

    fun scanInstalledModels(): Set<MaiaModelSpec> {
        return MaiaModelSpec.entries.filter { isModelInstalled(it) }.toSet()
    }

    fun refreshInstalledModels() {
        _installedModels.value = scanInstalledModels()
    }

    fun totalInstalledStorageMb(): Float {
        val bytes = MaiaModelSpec.entries.sumOf { spec ->
            val f = modelFile(spec)
            if (f.exists()) f.length() else 0L
        }
        return bytes / (1024f * 1024f)
    }

    suspend fun downloadModel(spec: MaiaModelSpec): Result<Unit> = withContext(Dispatchers.IO) {
        val targetFile = modelFile(spec)
        val tempFile = File(modelDir, "${spec.fileName}.tmp")
        try {
            _downloadState.value = MaiaDownloadState.Downloading(spec, 0f, 0f, spec.sizeMb.toFloat())

            val request = Request.Builder()
                .url(spec.downloadUrl)
                .header("User-Agent", "ChessAnalystAndroid/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = "Download failed (HTTP ${response.code}). Check internet connection."
                    _downloadState.value = MaiaDownloadState.Error(spec, msg)
                    return@withContext Result.failure(IllegalStateException(msg))
                }
                val body = response.body ?: run {
                    val msg = "Empty response from model server."
                    _downloadState.value = MaiaDownloadState.Error(spec, msg)
                    return@withContext Result.failure(IllegalStateException(msg))
                }
                val contentLength = body.contentLength().takeIf { it > 0L } ?: (spec.sizeMb * 1024L * 1024L)
                val totalMb = contentLength / (1024f * 1024f)

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var downloaded = 0L
                        var lastReport = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastReport >= 120L) {
                                lastReport = now
                                val progress = (downloaded.toFloat() / contentLength.toFloat()).coerceIn(0f, 0.99f)
                                _downloadState.value = MaiaDownloadState.Downloading(
                                    model = spec,
                                    progress = progress,
                                    downloadedMb = downloaded / (1024f * 1024f),
                                    totalMb = totalMb
                                )
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            refreshInstalledModels()
            _downloadState.value = MaiaDownloadState.Idle
            Result.success(Unit)
        } catch (ce: CancellationException) {
            tempFile.delete()
            _downloadState.value = MaiaDownloadState.Idle
            throw ce
        } catch (e: Exception) {
            tempFile.delete()
            val msg = e.localizedMessage ?: "Network error while downloading ${spec.displayName}."
            _downloadState.value = MaiaDownloadState.Error(spec, msg)
            Result.failure(e)
        }
    }

    suspend fun deleteModel(spec: MaiaModelSpec) = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            if (loadedModel == spec) {
                closeSessionLocked()
            }
            modelFile(spec).delete()
            File(modelDir, "${spec.fileName}.tmp").delete()
            refreshInstalledModels()
        }
    }

    suspend fun releaseMemory() = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            closeSessionLocked()
        }
    }

    private fun closeSessionLocked() {
        try { ortSession?.close() } catch (_: Exception) {}
        ortSession = null
        loadedModel = null
    }

    private fun ensureSessionLoadedLocked(spec: MaiaModelSpec): OrtSession? {
        if (loadedModel == spec && ortSession != null) return ortSession
        val file = modelFile(spec)
        if (!isModelInstalled(spec)) return null

        closeSessionLocked()
        return try {
            val env = ortEnv ?: OrtEnvironment.getEnvironment().also { ortEnv = it }
            val opts = OrtSession.SessionOptions().apply {
                val safeThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 2)
                setIntraOpNumThreads(safeThreads)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }
            val session = env.createSession(file.absolutePath, opts)
            ortSession = session
            loadedModel = spec
            session
        } catch (e: Exception) {
            Log.e("Maia3Engine", "Failed to load Maia-3 ONNX session for ${spec.displayName}", e)
            closeSessionLocked()
            null
        }
    }

    /**
     * Runs local Maia-3 ONNX inference on [fen] conditioned on [targetElo].
     * Returns null if the requested model is not downloaded or fails to load.
     */
    suspend fun predictHumanMoves(
        fen: String,
        spec: MaiaModelSpec,
        targetElo: Int = 1500,
        topK: Int = 4
    ): MaiaPrediction? = withContext(Dispatchers.Default) {
        val parsed = ChessBoardState.fromFen(fen)
        val board = (parsed as? FenValidationResult.Valid)?.state ?: return@withContext null
        val legalMoves = board.generateLegalMoves()
        if (legalMoves.isEmpty()) return@withContext null

        sessionMutex.withLock {
            val session = ensureSessionLoadedLocked(spec) ?: return@withContext null
            val env = ortEnv ?: return@withContext null

            try {
                val clampedElo = targetElo.coerceIn(600, 2600).toFloat()
                val tokens = encodeBoardTokens(board)
                val inputNames = session.inputNames

                val inputs = mutableMapOf<String, OnnxTensor>()
                val tokensTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(tokens), longArrayOf(1, 64, 12))
                inputs[resolveInputName(inputNames, "tokens", 0)] = tokensTensor

                val eloSelfName = inputNames.firstOrNull { it.contains("self", ignoreCase = true) || it.equals("elo", ignoreCase = true) }
                val eloOppoName = inputNames.firstOrNull { it.contains("oppo", ignoreCase = true) }

                if (eloSelfName != null) {
                    inputs[eloSelfName] = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(clampedElo)), longArrayOf(1))
                }
                if (eloOppoName != null) {
                    inputs[eloOppoName] = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(clampedElo)), longArrayOf(1))
                }

                val prediction: MaiaPrediction? = try {
                    session.run(inputs).use { result ->
                        val moveLogits = extractFirstFloatArray(result, preferredName = "logits_move", fallbackIndex = 0)
                            ?: return@use null
                        val valueLogits = extractFirstFloatArray(result, preferredName = "logits_value", fallbackIndex = 1)

                        val scoredMoves = scoreLegalMoves(board, legalMoves, moveLogits)
                        val topCandidates = scoredMoves.take(topK)

                        var winP: Float? = null
                        var drawP: Float? = null
                        var lossP: Float? = null
                        if (valueLogits != null && valueLogits.size >= 3) {
                            val wdl = softmax(floatArrayOf(valueLogits[0], valueLogits[1], valueLogits[2]))
                            lossP = wdl[0]
                            drawP = wdl[1]
                            winP = wdl[2]
                        }

                        MaiaPrediction(
                            fen = fen,
                            targetElo = targetElo,
                            modelName = spec.displayName,
                            candidates = topCandidates,
                            winProbability = winP,
                            drawProbability = drawP,
                            lossProbability = lossP
                        )
                    }
                } finally {
                    inputs.values.forEach { tensor ->
                        try { tensor.close() } catch (_: Exception) {}
                    }
                }
                prediction
            } catch (e: Exception) {
                Log.e("Maia3Engine", "Maia-3 inference failed", e)
                null
            }
        }
    }

    private fun resolveInputName(names: Set<String>, preferred: String, index: Int): String {
        if (preferred in names) return preferred
        return names.elementAtOrNull(index) ?: preferred
    }

    /**
     * Encodes board state into Maia-3 `[1, 64, 12]` one-hot piece channels,
     * mirrored vertically when Black is to move so index 0..5 = own P,N,B,R,Q,K
     * and 6..11 = opponent P,N,B,R,Q,K.
     */
    internal fun encodeBoardTokens(board: ChessBoardState): FloatArray {
        val data = FloatArray(64 * 12)
        val mirror = board.sideToMove == PieceColor.BLACK
        val us = board.sideToMove

        for (sq in 0..63) {
            val piece = board.squares[sq] ?: continue
            val file = sq and 7
            val rank = sq ushr 3
            val mappedRank = if (mirror) 7 - rank else rank
            val mappedSq = mappedRank * 8 + file

            val channelOffset = if (piece.color == us) 0 else 6
            val channel = channelOffset + piece.type.ordinal
            data[mappedSq * 12 + channel] = 1f
        }
        return data
    }

    /**
     * Maps legal moves into Maia-3's 4,352-entry policy head:
     * - Base moves: `0..4095` = `fromSq * 64 + toSq` (in perspective-mirrored coordinates)
     * - Promotion moves: `4096..4351` (64 from-rank7->rank8 pawn transitions * 4 promotion types)
     */
    internal fun moveToPolicyIndex(move: ChessMove, sideToMove: PieceColor, vocabSize: Int): Int {
        val mirror = sideToMove == PieceColor.BLACK
        val fromRank = if (mirror) 7 - move.from.rank else move.from.rank
        val toRank = if (mirror) 7 - move.to.rank else move.to.rank
        val fromIdx = fromRank * 8 + move.from.file
        val toIdx = toRank * 8 + move.to.file

        if (move.promotion != null && vocabSize >= 4352) {
            val promoOffset = when (move.promotion) {
                PieceType.QUEEN -> 0
                PieceType.ROOK -> 1
                PieceType.BISHOP -> 2
                PieceType.KNIGHT -> 3
                else -> 0
            }
            val promoBase = 4096 + (move.from.file * 8 + move.to.file) * 4 + promoOffset
            if (promoBase in 0 until vocabSize) return promoBase
        }
        return (fromIdx * 64 + toIdx).coerceIn(0, vocabSize - 1)
    }

    private fun scoreLegalMoves(
        board: ChessBoardState,
        legalMoves: List<ChessMove>,
        logits: FloatArray
    ): List<MaiaMoveCandidate> {
        val rawScores = FloatArray(legalMoves.size) { i ->
            val idx = moveToPolicyIndex(legalMoves[i], board.sideToMove, logits.size)
            logits[idx]
        }
        val probs = softmax(rawScores)
        return legalMoves.indices
            .map { i ->
                val move = legalMoves[i]
                MaiaMoveCandidate(
                    uci = move.toUci(),
                    san = board.moveToSan(move, legalMoves),
                    probability = probs[i]
                )
            }
            .sortedByDescending { it.probability }
    }

    private fun softmax(values: FloatArray): FloatArray {
        if (values.isEmpty()) return floatArrayOf()
        var maxVal = Float.NEGATIVE_INFINITY
        for (v in values) if (v > maxVal) maxVal = v
        val exps = FloatArray(values.size)
        var sum = 0.0
        for (i in values.indices) {
            val e = exp((values[i] - maxVal).toDouble())
            exps[i] = e.toFloat()
            sum += e
        }
        if (sum <= 0.0) {
            val uniform = 1f / values.size
            return FloatArray(values.size) { uniform }
        }
        for (i in exps.indices) {
            exps[i] = (exps[i] / sum).toFloat()
        }
        return exps
    }

    private fun extractFirstFloatArray(
        result: OrtSession.Result,
        preferredName: String,
        fallbackIndex: Int
    ): FloatArray? {
        val optValue = result.get(preferredName)
        val rawObj: Any? = if (optValue.isPresent) {
            optValue.get().value
        } else if (fallbackIndex < result.size()) {
            result[fallbackIndex].value
        } else null

        return when (rawObj) {
            is FloatArray -> rawObj
            is Array<*> -> {
                val first = rawObj.firstOrNull()
                if (first is FloatArray) first else null
            }
            else -> null
        }
    }
}
