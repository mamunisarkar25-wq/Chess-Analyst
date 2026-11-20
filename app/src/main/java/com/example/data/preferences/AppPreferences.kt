package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.chess.engine.MaiaModelSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "chess_analyst_prefs")

data class AnalystSettings(
    val searchDepth: Int = 4, // 3 (Fast), 4 (Balanced), 5 (Deep)
    val showCoordinates: Boolean = true,
    val showBestMoveArrow: Boolean = true,
    val showMaiaArrow: Boolean = true,
    val selectedMaiaModel: MaiaModelSpec = MaiaModelSpec.MAIA3_5M,
    val maiaTargetElo: Int = 1500,
    val lastChessComUsername: String = ""
)

/**
 * Lightweight settings-only DataStore.
 * Strictly NEVER persists games, PGNs, or review history (no database architecture).
 */
class AppPreferences(private val context: Context) {

    private object Keys {
        val SEARCH_DEPTH = intPreferencesKey("search_depth")
        val SHOW_COORDINATES = booleanPreferencesKey("show_coordinates")
        val SHOW_BEST_ARROW = booleanPreferencesKey("show_best_arrow")
        val SHOW_MAIA_ARROW = booleanPreferencesKey("show_maia_arrow")
        val MAIA_MODEL_ID = stringPreferencesKey("maia_model_id")
        val MAIA_TARGET_ELO = intPreferencesKey("maia_target_elo")
        val LAST_CHESS_COM_USER = stringPreferencesKey("last_chess_com_user")
    }

    val settingsFlow: Flow<AnalystSettings> = context.settingsDataStore.data.map { prefs ->
        AnalystSettings(
            searchDepth = (prefs[Keys.SEARCH_DEPTH] ?: 4).coerceIn(3, 5),
            showCoordinates = prefs[Keys.SHOW_COORDINATES] ?: true,
            showBestMoveArrow = prefs[Keys.SHOW_BEST_ARROW] ?: true,
            showMaiaArrow = prefs[Keys.SHOW_MAIA_ARROW] ?: true,
            selectedMaiaModel = MaiaModelSpec.fromId(prefs[Keys.MAIA_MODEL_ID] ?: MaiaModelSpec.MAIA3_5M.id),
            maiaTargetElo = (prefs[Keys.MAIA_TARGET_ELO] ?: 1500).coerceIn(600, 2600),
            lastChessComUsername = prefs[Keys.LAST_CHESS_COM_USER] ?: ""
        )
    }

    suspend fun updateSearchDepth(depth: Int) {
        context.settingsDataStore.edit { it[Keys.SEARCH_DEPTH] = depth.coerceIn(3, 5) }
    }

    suspend fun updateShowCoordinates(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_COORDINATES] = show }
    }

    suspend fun updateShowBestArrow(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_BEST_ARROW] = show }
    }

    suspend fun updateShowMaiaArrow(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_MAIA_ARROW] = show }
    }

    suspend fun updateSelectedMaiaModel(spec: MaiaModelSpec) {
        context.settingsDataStore.edit { it[Keys.MAIA_MODEL_ID] = spec.id }
    }

    suspend fun updateMaiaTargetElo(elo: Int) {
        context.settingsDataStore.edit { it[Keys.MAIA_TARGET_ELO] = elo.coerceIn(600, 2600) }
    }

    suspend fun updateLastChessComUsername(username: String) {
        context.settingsDataStore.edit { it[Keys.LAST_CHESS_COM_USER] = username.trim() }
    }
}
