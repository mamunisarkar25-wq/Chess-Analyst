package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chess.analysis.GameAnalyzer
import com.example.chess.analysis.GameReviewReport
import com.example.chess.analysis.ReviewProgress
import com.example.chess.core.CastlingRights
import com.example.chess.core.ChessBoardState
import com.example.chess.core.ChessMove
import com.example.chess.core.FenValidationResult
import com.example.chess.core.ParsedPgnGame
import com.example.chess.core.PgnParseResult
import com.example.chess.core.PgnParser
import com.example.chess.core.Piece
import com.example.chess.core.PieceColor
import com.example.chess.core.Square
import com.example.chess.engine.EngineEvaluation
import com.example.chess.engine.Maia3Engine
import com.example.chess.engine.MaiaDownloadState
import com.example.chess.engine.MaiaModelSpec
import com.example.chess.engine.MaiaPrediction
import com.example.chess.engine.StockfishUciEngine
import com.example.data.chesscom.ChessComFetchResult
import com.example.data.chesscom.ChessComPlayerSummary
import com.example.data.chesscom.ChessComRecentGame
import com.example.data.chesscom.ChessComRepository
import com.example.data.preferences.AnalystSettings
import com.example.data.preferences.AppPreferences
import com.example.data.session.InMemorySessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AppScreen {
    data object Home : AppScreen()
    data object PgnImport : AppScreen()
    data object FenImport : AppScreen()
    data object ChessCom : AppScreen()
    data object GameReview : AppScreen()
}

data class PgnImportUiState(
    val rawText: String = "",
    val parsedGames: List<ParsedPgnGame> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errorMessage: String? = null,
    val selectedFileName: String? = null
)

data class FenImportUiState(
    val fenInput: String = ChessBoardState.STARTING_FEN,
    val board: ChessBoardState = ChessBoardState.initial(),
    val validationError: String? = null,
    val isAnalyzeMode: Boolean = false,
    val selectedEditorPiece: Piece? = Piece(PieceColor.WHITE, com.example.chess.core.PieceType.QUEEN),
    val isEraseTool: Boolean = false,
    val flipped: Boolean = false,
    val isAnalyzing: Boolean = false,
    val evaluation: EngineEvaluation? = null,
    val maiaPrediction: MaiaPrediction? = null,
    val moveHistory: List<Pair<ChessMove, ChessBoardState>> = emptyList(),
    val lastMove: ChessMove? = null
)

data class ChessComUiState(
    val usernameInput: String = "",
    val isLoading: Boolean = false,
    val player: ChessComPlayerSummary? = null,
    val games: List<ChessComRecentGame> = emptyList(),
    val errorMessage: String? = null,
    val isRateLimited: Boolean = false,
    val hasSearched: Boolean = false
)

data class VariationStep(
    val move: ChessMove,
    val san: String,
    val boardAfter: ChessBoardState
)

data class GameReviewUiState(
    val activeGame: ParsedPgnGame? = null,
    val report: GameReviewReport? = null,
    val isAnalyzing: Boolean = false,
    val progress: ReviewProgress? = null,
    val errorMessage: String? = null,
    val currentPly: Int = 0, // 0 = starting position, 1..N = after move N
    val flipped: Boolean = false,
    val selectedTab: Int = 0, // 0 = Review, 1 = Moves, 2 = Phases
    // Interactive variation exploration from current ply
    val variationSteps: List<VariationStep> = emptyList(),
    val variationEvaluation: EngineEvaluation? = null,
    val isEvaluatingVariation: Boolean = false,
    // Live Maia-3 prediction for the currently viewed position
    val currentMaiaPrediction: MaiaPrediction? = null,
    val isComputingMaia: Boolean = false
)

class ChessAnalystViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppPreferences(application)
    private val sessionStore = InMemorySessionStore()
    private val chessComRepo = ChessComRepository()
    private val stockfishEngine = StockfishUciEngine(application)
    private val maiaEngine = Maia3Engine(application)

    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Home)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _showEngineSettings = MutableStateFlow(false)
    val showEngineSettings: StateFlow<Boolean> = _showEngineSettings.asStateFlow()

    val settings: StateFlow<AnalystSettings> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AnalystSettings())

    val sessionRecentReviews: StateFlow<List<GameReviewReport>> = sessionStore.recentReviews
    val maiaDownloadState: StateFlow<MaiaDownloadState> = maiaEngine.downloadState
    val installedMaiaModels: StateFlow<Set<MaiaModelSpec>> = maiaEngine.installedModels

    private val _pgnState = MutableStateFlow(PgnImportUiState())
    val pgnState: StateFlow<PgnImportUiState> = _pgnState.asStateFlow()

    private val _fenState = MutableStateFlow(FenImportUiState())
    val fenState: StateFlow<FenImportUiState> = _fenState.asStateFlow()

    private val _chessComState = MutableStateFlow(ChessComUiState())
    val chessComState: StateFlow<ChessComUiState> = _chessComState.asStateFlow()

    private val _reviewState = MutableStateFlow(GameReviewUiState())
    val reviewState: StateFlow<GameReviewUiState> = _reviewState.asStateFlow()

    private var reviewJob: Job? = null
    private var fenEvalJob: Job? = null
    private var variationEvalJob: Job? = null
    private var maiaJob: Job? = null
    private var maiaDownloadJob: Job? = null

    val activeEngineLabel: String get() = stockfishEngine.activeEngineLabel

    init {
        viewModelScope.launch {
            preferences.settingsFlow.collect { s ->
                if (_chessComState.value.usernameInput.isBlank() && s.lastChessComUsername.isNotBlank()) {
                    _chessComState.update { it.copy(usernameInput = s.lastChessComUsername) }
                }
            }
        }
    }

    // --- Navigation ---

    fun navigateTo(screen: AppScreen) {
        if (screen != AppScreen.GameReview) {
            cancelReviewAnalysis()
        }
        if (screen != AppScreen.FenImport) {
            fenEvalJob?.cancel()
        }
        _currentScreen.value = screen
    }

    fun navigateBack() {
        when (_currentScreen.value) {
            AppScreen.Home -> Unit
            AppScreen.PgnImport, AppScreen.FenImport, AppScreen.ChessCom -> navigateTo(AppScreen.Home)
            AppScreen.GameReview -> {
                cancelReviewAnalysis()
                navigateTo(AppScreen.Home)
            }
        }
    }

    fun setShowEngineSettings(show: Boolean) {
        _showEngineSettings.value = show
    }

    // --- Settings & Maia-3 Model Manager ---

    fun updateSearchDepth(depth: Int) {
        viewModelScope.launch { preferences.updateSearchDepth(depth) }
    }

    fun updateShowCoordinates(show: Boolean) {
        viewModelScope.launch { preferences.updateShowCoordinates(show) }
    }

    fun updateShowBestArrow(show: Boolean) {
        viewModelScope.launch { preferences.updateShowBestArrow(show) }
    }

    fun updateShowMaiaArrow(show: Boolean) {
        viewModelScope.launch { preferences.updateShowMaiaArrow(show) }
    }

    fun selectMaiaModel(spec: MaiaModelSpec) {
        viewModelScope.launch {
            preferences.updateSelectedMaiaModel(spec)
            refreshMaiaForActivePosition()
        }
    }

    fun updateMaiaTargetElo(elo: Int) {
        viewModelScope.launch {
            preferences.updateMaiaTargetElo(elo)
            refreshMaiaForActivePosition()
        }
    }

    fun downloadMaiaModel(spec: MaiaModelSpec) {
        maiaDownloadJob?.cancel()
        maiaDownloadJob = viewModelScope.launch {
            val result = maiaEngine.downloadModel(spec)
            if (result.isSuccess) {
                preferences.updateSelectedMaiaModel(spec)
                refreshMaiaForActivePosition()
            }
        }
    }

    fun cancelMaiaDownload() {
        maiaDownloadJob?.cancel()
    }

    fun deleteMaiaModel(spec: MaiaModelSpec) {
        viewModelScope.launch {
            maiaEngine.deleteModel(spec)
            _reviewState.update { it.copy(currentMaiaPrediction = null) }
            _fenState.update { it.copy(maiaPrediction = null) }
        }
    }

    fun getMaiaStorageUsedMb(): Float = maiaEngine.totalInstalledStorageMb()

    // --- PGN Import ---

    fun updatePgnText(text: String, fileName: String? = null) {
        _pgnState.update {
            it.copy(
                rawText = text,
                selectedFileName = fileName ?: it.selectedFileName,
                errorMessage = null
            )
        }
    }

    fun parseCurrentPgn() {
        val raw = _pgnState.value.rawText
        when (val res = PgnParser.parseMultiple(raw)) {
            is PgnParseResult.Error -> {
                _pgnState.update {
                    it.copy(parsedGames = emptyList(), warnings = emptyList(), errorMessage = res.message)
                }
            }
            is PgnParseResult.Success -> {
                _pgnState.update {
                    it.copy(parsedGames = res.games, warnings = res.warnings, errorMessage = null)
                }
                if (res.games.size == 1 && res.warnings.isEmpty()) {
                    startGameReview(res.games.first(), sourceLabel = _pgnState.value.selectedFileName ?: "PGN Import")
                }
            }
        }
    }

    fun loadClassicMasterPgn() {
        val operaGamePgn = """
            [Event "Paris Opera House"]
            [Site "Paris FRA"]
            [Date "1858.10.21"]
            [White "Paul Morphy"]
            [Black "Duke Karl / Count Isouard"]
            [Result "1-0"]
            [WhiteElo "2600"]
            [BlackElo "1800"]

            1. e4 e5 2. Nf3 d6 3. d4 Bg4 4. dxe5 Bxf3 5. Qxf3 dxe5 6. Bc4 Nf6
            7. Qb3 Qe7 8. Nc3 c6 9. Bg5 b5 10. Nxb5 cxb5 11. Bxb5+ Nbd7
            12. O-O-O Rd8 13. Rxd7 Rxd7 14. Rd1 Qe6 15. Bxd7+ Nxd7
            16. Qb8+ Nxb8 17. Rd8# 1-0
        """.trimIndent()
        _pgnState.update {
            it.copy(rawText = operaGamePgn, selectedFileName = "Opera Game (1858)", errorMessage = null)
        }
        parseCurrentPgn()
    }

    fun clearPgnImport() {
        _pgnState.value = PgnImportUiState()
    }

    // --- FEN Import & Interactive Board Editor ---

    fun updateFenInputText(text: String) {
        when (val res = ChessBoardState.fromFen(text)) {
            is FenValidationResult.Valid -> {
                _fenState.update {
                    it.copy(
                        fenInput = text,
                        board = res.state,
                        validationError = null,
                        moveHistory = emptyList(),
                        lastMove = null
                    )
                }
                if (_fenState.value.isAnalyzeMode) {
                    analyzeCurrentFenPosition()
                }
            }
            is FenValidationResult.Invalid -> {
                _fenState.update { it.copy(fenInput = text, validationError = res.reason) }
            }
        }
    }

    fun selectEditorPiece(piece: Piece?) {
        _fenState.update { it.copy(selectedEditorPiece = piece, isEraseTool = piece == null) }
    }

    fun onFenEditorSquareTap(square: Square) {
        val cur = _fenState.value
        val existing = cur.board.pieceAt(square)
        val newPiece = when {
            cur.isEraseTool -> null
            existing == cur.selectedEditorPiece -> null
            else -> cur.selectedEditorPiece
        }
        val updatedBoard = cur.board.withPiece(square, newPiece)
        val validation = ChessBoardState.validateBoardForAnalysis(updatedBoard)
        _fenState.update {
            it.copy(
                board = updatedBoard,
                fenInput = updatedBoard.toFen(),
                validationError = (validation as? FenValidationResult.Invalid)?.reason,
                evaluation = null,
                maiaPrediction = null
            )
        }
    }

    fun setFenSideToMove(color: PieceColor) {
        val updated = _fenState.value.board.withSideToMove(color)
        val validation = ChessBoardState.validateBoardForAnalysis(updated)
        _fenState.update {
            it.copy(
                board = updated,
                fenInput = updated.toFen(),
                validationError = (validation as? FenValidationResult.Invalid)?.reason
            )
        }
        if (_fenState.value.isAnalyzeMode && validation is FenValidationResult.Valid) {
            analyzeCurrentFenPosition()
        }
    }

    fun updateFenCastling(rights: CastlingRights) {
        val updated = _fenState.value.board.withCastling(rights)
        val validation = ChessBoardState.validateBoardForAnalysis(updated)
        _fenState.update {
            it.copy(
                board = updated,
                fenInput = updated.toFen(),
                validationError = (validation as? FenValidationResult.Invalid)?.reason
            )
        }
    }

    fun resetFenToStart() {
        val start = ChessBoardState.initial()
        _fenState.update {
            it.copy(
                board = start,
                fenInput = start.toFen(),
                validationError = null,
                moveHistory = emptyList(),
                lastMove = null,
                evaluation = null,
                maiaPrediction = null
            )
        }
        if (_fenState.value.isAnalyzeMode) analyzeCurrentFenPosition()
    }

    fun clearFenBoard() {
        val empty = ChessBoardState.empty()
        fenEvalJob?.cancel()
        _fenState.update {
            it.copy(
                board = empty,
                fenInput = empty.toFen(),
                validationError = "Add both a White king and a Black king before analysis.",
                isAnalyzeMode = false,
                moveHistory = emptyList(),
                lastMove = null,
                evaluation = null,
                maiaPrediction = null
            )
        }
    }

    fun toggleFenFlip() {
        _fenState.update { it.copy(flipped = !it.flipped) }
    }

    fun setFenAnalyzeMode(analyze: Boolean) {
        if (analyze) {
            when (val res = ChessBoardState.validateBoardForAnalysis(_fenState.value.board)) {
                is FenValidationResult.Invalid -> {
                    _fenState.update { it.copy(validationError = res.reason, isAnalyzeMode = false) }
                    return
                }
                is FenValidationResult.Valid -> {
                    _fenState.update {
                        it.copy(
                            board = res.state,
                            fenInput = res.state.toFen(),
                            validationError = null,
                            isAnalyzeMode = true,
                            moveHistory = emptyList(),
                            lastMove = null
                        )
                    }
                    analyzeCurrentFenPosition()
                }
            }
        } else {
            fenEvalJob?.cancel()
            _fenState.update { it.copy(isAnalyzeMode = false, isAnalyzing = false) }
        }
    }

    fun playMoveOnFenBoard(move: ChessMove) {
        val cur = _fenState.value
        val nextBoard = cur.board.applyMoveUnchecked(move)
        _fenState.update {
            it.copy(
                board = nextBoard,
                fenInput = nextBoard.toFen(),
                moveHistory = cur.moveHistory + (move to cur.board),
                lastMove = move,
                validationError = null
            )
        }
        analyzeCurrentFenPosition()
    }

    fun undoFenMove() {
        val cur = _fenState.value
        if (cur.moveHistory.isEmpty()) return
        val previous = cur.moveHistory.last().second
        val remaining = cur.moveHistory.dropLast(1)
        _fenState.update {
            it.copy(
                board = previous,
                fenInput = previous.toFen(),
                moveHistory = remaining,
                lastMove = remaining.lastOrNull()?.first
            )
        }
        analyzeCurrentFenPosition()
    }

    fun analyzeCurrentFenPosition() {
        val cur = _fenState.value
        val validated = ChessBoardState.validateBoardForAnalysis(cur.board)
        if (validated is FenValidationResult.Invalid) {
            _fenState.update { it.copy(validationError = validated.reason, isAnalyzing = false) }
            return
        }
        val validBoard = (validated as FenValidationResult.Valid).state
        val fen = validBoard.toFen()
        val s = settings.value

        fenEvalJob?.cancel()
        fenEvalJob = viewModelScope.launch {
            _fenState.update { it.copy(isAnalyzing = true, validationError = null) }
            try {
                val eval = stockfishEngine.evaluatePosition(
                    fen = fen,
                    depth = (s.searchDepth + 1).coerceAtMost(6),
                    timeLimitMs = 1100L,
                    multiPv = 3
                )
                _fenState.update { it.copy(evaluation = eval, isAnalyzing = false) }

                // Run optional Maia-3 local human prediction if installed
                val maiaPred = if (maiaEngine.isModelInstalled(s.selectedMaiaModel)) {
                    maiaEngine.predictHumanMoves(fen, s.selectedMaiaModel, s.maiaTargetElo)
                } else null
                _fenState.update { it.copy(maiaPrediction = maiaPred) }
            } catch (_: CancellationException) {
                _fenState.update { it.copy(isAnalyzing = false) }
            } catch (e: Exception) {
                _fenState.update { it.copy(isAnalyzing = false, validationError = e.localizedMessage) }
            }
        }
    }

    // --- Chess.com Published-Data API ---

    fun updateChessComUsername(username: String) {
        _chessComState.update { it.copy(usernameInput = username, errorMessage = null) }
    }

    fun fetchChessComGames() {
        val username = _chessComState.value.usernameInput.trim()
        val formatErr = chessComRepo.validateUsernameFormat(username)
        if (formatErr != null) {
            _chessComState.update { it.copy(errorMessage = formatErr) }
            return
        }

        viewModelScope.launch {
            _chessComState.update { it.copy(isLoading = true, errorMessage = null, isRateLimited = false) }
            when (val result = chessComRepo.fetchLatestGames(username, maxGames = 10)) {
                is ChessComFetchResult.Success -> {
                    preferences.updateLastChessComUsername(result.player.username)
                    _chessComState.update {
                        it.copy(
                            isLoading = false,
                            player = result.player,
                            games = result.games,
                            errorMessage = null,
                            hasSearched = true
                        )
                    }
                }
                is ChessComFetchResult.Error -> {
                    _chessComState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                            isRateLimited = result.isRateLimited,
                            hasSearched = true
                        )
                    }
                }
            }
        }
    }

    fun analyzeChessComGame(recentGame: ChessComRecentGame) {
        val parsed = recentGame.parsedGame ?: when (val p = PgnParser.parseMultiple(recentGame.rawPgn)) {
            is PgnParseResult.Success -> p.games.firstOrNull()
            is PgnParseResult.Error -> null
        }
        if (parsed == null) {
            _chessComState.update { it.copy(errorMessage = "Could not parse this game's PGN for analysis.") }
            return
        }
        startGameReview(
            game = parsed,
            sourceLabel = "Chess.com (${recentGame.timeControlDisplay} ${recentGame.timeCategory.label})",
            flipForBlack = recentGame.playerSide == PieceColor.BLACK
        )
    }

    // --- Full Game Review & Variation Exploration ---

    fun openSessionReview(report: GameReviewReport) {
        cancelReviewAnalysis()
        _reviewState.value = GameReviewUiState(
            activeGame = report.game,
            report = report,
            isAnalyzing = false,
            currentPly = report.moves.size,
            flipped = false,
            selectedTab = 0
        )
        _currentScreen.value = AppScreen.GameReview
        refreshMaiaForActivePosition()
    }

    fun startGameReview(
        game: ParsedPgnGame,
        sourceLabel: String = "PGN Import",
        flipForBlack: Boolean = false
    ) {
        cancelReviewAnalysis()
        _reviewState.value = GameReviewUiState(
            activeGame = game,
            report = null,
            isAnalyzing = true,
            progress = ReviewProgress(0, game.moves.size, "Initializing local engine..."),
            currentPly = 0,
            flipped = flipForBlack,
            selectedTab = 0
        )
        _currentScreen.value = AppScreen.GameReview

        val depth = settings.value.searchDepth
        val timePerMove = when (depth) {
            3 -> 160L
            4 -> 320L
            else -> 580L
        }

        reviewJob = viewModelScope.launch {
            try {
                val report = GameAnalyzer.analyzeGame(
                    game = game,
                    engine = stockfishEngine,
                    searchDepth = depth,
                    timePerMoveMs = timePerMove,
                    sourceLabel = sourceLabel,
                    onProgress = { progress ->
                        _reviewState.update { it.copy(progress = progress) }
                    }
                )
                sessionStore.addReview(report)
                _reviewState.update {
                    it.copy(
                        report = report,
                        isAnalyzing = false,
                        progress = null,
                        currentPly = if (report.moves.isNotEmpty()) 1 else 0
                    )
                }
                refreshMaiaForActivePosition()
            } catch (_: CancellationException) {
                _reviewState.update {
                    it.copy(isAnalyzing = false, progress = null, errorMessage = "Analysis cancelled.")
                }
            } catch (e: Exception) {
                _reviewState.update {
                    it.copy(
                        isAnalyzing = false,
                        progress = null,
                        errorMessage = "Analysis error: ${e.localizedMessage ?: "Engine stopped"}"
                    )
                }
            }
        }
    }

    fun cancelReviewAnalysis() {
        stockfishEngine.cancelCurrentAnalysis()
        reviewJob?.cancel()
        reviewJob = null
        variationEvalJob?.cancel()
        maiaJob?.cancel()
    }

    fun selectReviewTab(tab: Int) {
        _reviewState.update { it.copy(selectedTab = tab) }
    }

    fun toggleReviewFlip() {
        _reviewState.update { it.copy(flipped = !it.flipped) }
    }

    fun jumpToPly(ply: Int) {
        val game = _reviewState.value.activeGame ?: return
        val clamped = ply.coerceIn(0, game.moves.size)
        variationEvalJob?.cancel()
        _reviewState.update {
            it.copy(
                currentPly = clamped,
                variationSteps = emptyList(),
                variationEvaluation = null,
                isEvaluatingVariation = false
            )
        }
        refreshMaiaForActivePosition()
    }

    fun stepForward() = jumpToPly(_reviewState.value.currentPly + 1)
    fun stepBackward() {
        val state = _reviewState.value
        if (state.variationSteps.isNotEmpty()) {
            exitVariationStep()
        } else {
            jumpToPly(state.currentPly - 1)
        }
    }

    fun getCurrentReviewBoard(): ChessBoardState {
        val state = _reviewState.value
        if (state.variationSteps.isNotEmpty()) {
            return state.variationSteps.last().boardAfter
        }
        val game = state.activeGame ?: return ChessBoardState.initial()
        val fen = if (state.currentPly <= 0) {
            game.initialFen
        } else {
            game.moves.getOrNull(state.currentPly - 1)?.fenAfter ?: game.initialFen
        }
        return (ChessBoardState.fromFen(fen) as? FenValidationResult.Valid)?.state ?: ChessBoardState.initial()
    }

    /**
     * Allows interactive exploration of alternative variations right on the Game Review board.
     */
    fun playVariationMove(move: ChessMove) {
        val state = _reviewState.value
        val curBoard = getCurrentReviewBoard()

        // If not in a variation and user played the exact next game move, simply advance the game!
        if (state.variationSteps.isEmpty()) {
            val nextGameMove = state.activeGame?.moves?.getOrNull(state.currentPly)
            if (nextGameMove != null && nextGameMove.uci == move.toUci()) {
                jumpToPly(state.currentPly + 1)
                return
            }
        }

        val san = curBoard.moveToSan(move)
        val nextBoard = curBoard.applyMoveUnchecked(move)
        val step = VariationStep(move, san, nextBoard)

        _reviewState.update {
            it.copy(
                variationSteps = it.variationSteps + step,
                isEvaluatingVariation = true
            )
        }
        evaluateVariationBoard(nextBoard.toFen())
        refreshMaiaForActivePosition()
    }

    fun exitVariationStep() {
        val cur = _reviewState.value
        if (cur.variationSteps.isEmpty()) return
        val remaining = cur.variationSteps.dropLast(1)
        variationEvalJob?.cancel()
        _reviewState.update {
            it.copy(
                variationSteps = remaining,
                variationEvaluation = null,
                isEvaluatingVariation = remaining.isNotEmpty()
            )
        }
        if (remaining.isNotEmpty()) {
            evaluateVariationBoard(remaining.last().boardAfter.toFen())
        }
        refreshMaiaForActivePosition()
    }

    fun clearVariation() {
        variationEvalJob?.cancel()
        _reviewState.update {
            it.copy(variationSteps = emptyList(), variationEvaluation = null, isEvaluatingVariation = false)
        }
        refreshMaiaForActivePosition()
    }

    private fun evaluateVariationBoard(fen: String) {
        val depth = settings.value.searchDepth
        variationEvalJob?.cancel()
        variationEvalJob = viewModelScope.launch {
            try {
                val eval = stockfishEngine.evaluatePosition(
                    fen = fen,
                    depth = (depth + 1).coerceAtMost(5),
                    timeLimitMs = 700L,
                    multiPv = 2
                )
                _reviewState.update { it.copy(variationEvaluation = eval, isEvaluatingVariation = false) }
            } catch (_: CancellationException) {
                _reviewState.update { it.copy(isEvaluatingVariation = false) }
            }
        }
    }

    private fun refreshMaiaForActivePosition() {
        val s = settings.value
        val spec = s.selectedMaiaModel
        if (!maiaEngine.isModelInstalled(spec)) {
            _reviewState.update { it.copy(currentMaiaPrediction = null, isComputingMaia = false) }
            return
        }

        if (_currentScreen.value == AppScreen.GameReview) {
            val fen = getCurrentReviewBoard().toFen()
            maiaJob?.cancel()
            maiaJob = viewModelScope.launch {
                _reviewState.update { it.copy(isComputingMaia = true) }
                val pred = maiaEngine.predictHumanMoves(fen, spec, s.maiaTargetElo, topK = 4)
                _reviewState.update { it.copy(currentMaiaPrediction = pred, isComputingMaia = false) }
            }
        } else if (_currentScreen.value == AppScreen.FenImport && _fenState.value.isAnalyzeMode) {
            val fen = _fenState.value.board.toFen()
            maiaJob?.cancel()
            maiaJob = viewModelScope.launch {
                val pred = maiaEngine.predictHumanMoves(fen, spec, s.maiaTargetElo, topK = 4)
                _fenState.update { it.copy(maiaPrediction = pred) }
            }
        }
    }

    fun exportCurrentReviewPgn(): String? {
        val report = _reviewState.value.report ?: return null
        return PgnParser.exportAnnotatedPgn(report)
    }

    fun onLowMemory() {
        stockfishEngine.clearMemory()
        viewModelScope.launch { maiaEngine.releaseMemory() }
    }

    override fun onCleared() {
        super.onCleared()
        stockfishEngine.shutdown()
        viewModelScope.launch { maiaEngine.releaseMemory() }
    }
}
