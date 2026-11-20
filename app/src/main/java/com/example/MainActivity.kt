package com.example

import android.content.ComponentCallbacks2
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.ChessComScreen
import com.example.ui.screens.EngineSettingsSheet
import com.example.ui.screens.FenImportScreen
import com.example.ui.screens.GameReviewScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PgnImportScreen
import com.example.ui.theme.ChessAnalystTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.ChessAnalystViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ChessAnalystViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChessAnalystTheme(darkTheme = true) {
                Surface(
                    color = ObsidianBg,
                    modifier = Modifier.fillMaxSize()
                ) {
                    ChessAnalystApp(viewModel = viewModel)
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            viewModel.onLowMemory()
        }
    }
}

@Composable
fun ChessAnalystApp(viewModel: ChessAnalystViewModel) {
    val screen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recentReviews by viewModel.sessionRecentReviews.collectAsStateWithLifecycle()
    val pgnState by viewModel.pgnState.collectAsStateWithLifecycle()
    val fenState by viewModel.fenState.collectAsStateWithLifecycle()
    val chessComState by viewModel.chessComState.collectAsStateWithLifecycle()
    val reviewState by viewModel.reviewState.collectAsStateWithLifecycle()
    val showEngineSettings by viewModel.showEngineSettings.collectAsStateWithLifecycle()
    val installedMaiaModels by viewModel.installedMaiaModels.collectAsStateWithLifecycle()
    val maiaDownloadState by viewModel.maiaDownloadState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        when (screen) {
            AppScreen.Home -> {
                HomeScreen(
                    recentReviews = recentReviews,
                    installedMaiaModels = installedMaiaModels,
                    selectedMaiaModel = settings.selectedMaiaModel,
                    searchDepth = settings.searchDepth,
                    onImportPgnClick = { viewModel.navigateTo(AppScreen.PgnImport) },
                    onImportFenClick = { viewModel.navigateTo(AppScreen.FenImport) },
                    onChessComClick = { viewModel.navigateTo(AppScreen.ChessCom) },
                    onOpenRecentReview = { viewModel.openSessionReview(it) },
                    onOpenEngineSettings = { viewModel.setShowEngineSettings(true) }
                )
            }

            AppScreen.PgnImport -> {
                PgnImportScreen(
                    state = pgnState,
                    onBack = { viewModel.navigateBack() },
                    onTextChange = { text, file -> viewModel.updatePgnText(text, file) },
                    onParseClick = { viewModel.parseCurrentPgn() },
                    onSelectGame = { game ->
                        viewModel.startGameReview(game, sourceLabel = pgnState.selectedFileName ?: "PGN Import")
                    },
                    onLoadClassicGame = { viewModel.loadClassicMasterPgn() },
                    onClear = { viewModel.clearPgnImport() }
                )
            }

            AppScreen.FenImport -> {
                FenImportScreen(
                    state = fenState,
                    showCoordinates = settings.showCoordinates,
                    showBestArrow = settings.showBestMoveArrow,
                    showMaiaArrow = settings.showMaiaArrow,
                    installedMaiaModels = installedMaiaModels,
                    selectedMaiaModel = settings.selectedMaiaModel,
                    maiaTargetElo = settings.maiaTargetElo,
                    onBack = { viewModel.navigateBack() },
                    onFenInputChange = { viewModel.updateFenInputText(it) },
                    onSelectEditorPiece = { viewModel.selectEditorPiece(it) },
                    onEditorSquareTap = { viewModel.onFenEditorSquareTap(it) },
                    onSideToMoveChange = { viewModel.setFenSideToMove(it) },
                    onCastlingChange = { viewModel.updateFenCastling(it) },
                    onResetBoard = { viewModel.resetFenToStart() },
                    onClearBoard = { viewModel.clearFenBoard() },
                    onToggleFlip = { viewModel.toggleFenFlip() },
                    onToggleAnalyzeMode = { viewModel.setFenAnalyzeMode(it) },
                    onPlayMove = { viewModel.playMoveOnFenBoard(it) },
                    onUndoMove = { viewModel.undoFenMove() },
                    onOpenEngineSettings = { viewModel.setShowEngineSettings(true) }
                )
            }

            AppScreen.ChessCom -> {
                ChessComScreen(
                    state = chessComState,
                    onBack = { viewModel.navigateBack() },
                    onUsernameChange = { viewModel.updateChessComUsername(it) },
                    onFetchGames = { viewModel.fetchChessComGames() },
                    onAnalyzeGame = { viewModel.analyzeChessComGame(it) }
                )
            }

            AppScreen.GameReview -> {
                GameReviewScreen(
                    state = reviewState,
                    currentBoard = viewModel.getCurrentReviewBoard(),
                    showCoordinates = settings.showCoordinates,
                    showBestArrow = settings.showBestMoveArrow,
                    showMaiaArrow = settings.showMaiaArrow,
                    installedMaiaModels = installedMaiaModels,
                    selectedMaiaModel = settings.selectedMaiaModel,
                    maiaTargetElo = settings.maiaTargetElo,
                    onBack = { viewModel.navigateBack() },
                    onCancelAnalysis = { viewModel.cancelReviewAnalysis() },
                    onSelectTab = { viewModel.selectReviewTab(it) },
                    onToggleFlip = { viewModel.toggleReviewFlip() },
                    onJumpToPly = { viewModel.jumpToPly(it) },
                    onStepBack = { viewModel.stepBackward() },
                    onStepForward = { viewModel.stepForward() },
                    onPlayVariationMove = { viewModel.playVariationMove(it) },
                    onClearVariation = { viewModel.clearVariation() },
                    onExportAnnotatedPgn = { viewModel.exportCurrentReviewPgn() },
                    onOpenEngineSettings = { viewModel.setShowEngineSettings(true) }
                )
            }
        }
    }

    if (showEngineSettings) {
        EngineSettingsSheet(
            settings = settings,
            activeEngineLabel = viewModel.activeEngineLabel,
            installedMaiaModels = installedMaiaModels,
            downloadState = maiaDownloadState,
            storageUsedMb = viewModel.getMaiaStorageUsedMb(),
            onDismiss = { viewModel.setShowEngineSettings(false) },
            onDepthChange = { viewModel.updateSearchDepth(it) },
            onShowCoordinatesChange = { viewModel.updateShowCoordinates(it) },
            onShowBestArrowChange = { viewModel.updateShowBestArrow(it) },
            onShowMaiaArrowChange = { viewModel.updateShowMaiaArrow(it) },
            onSelectMaiaModel = { viewModel.selectMaiaModel(it) },
            onDownloadMaiaModel = { viewModel.downloadMaiaModel(it) },
            onCancelMaiaDownload = { viewModel.cancelMaiaDownload() },
            onDeleteMaiaModel = { viewModel.deleteMaiaModel(it) },
            onMaiaEloChange = { viewModel.updateMaiaTargetElo(it) }
        )
    }
}
