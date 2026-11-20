package com.example.data.session

import com.example.chess.analysis.GameReviewReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Strictly in-memory store for games analyzed during the current app session.
 * All data is discarded automatically when the app process exits (no database).
 */
class InMemorySessionStore {

    private val _recentReviews = MutableStateFlow<List<GameReviewReport>>(emptyList())
    val recentReviews: StateFlow<List<GameReviewReport>> = _recentReviews.asStateFlow()

    fun addReview(report: GameReviewReport) {
        val updated = buildList {
            add(report)
            addAll(_recentReviews.value.filterNot { it.id == report.id })
        }.take(20)
        _recentReviews.value = updated
    }

    fun getById(id: String): GameReviewReport? = _recentReviews.value.firstOrNull { it.id == id }

    fun clearSession() {
        _recentReviews.value = emptyList()
    }
}
