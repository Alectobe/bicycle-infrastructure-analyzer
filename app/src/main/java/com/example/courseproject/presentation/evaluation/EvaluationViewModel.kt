package com.example.courseproject.presentation.evaluation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseproject.domain.model.AreaEvaluation
import com.example.courseproject.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.abs

/** Состояние экранов результата и детализации. */
data class EvaluationUiState(
    val evaluation: AreaEvaluation? = null,
    val similar: List<AreaEvaluation> = emptyList(),
)

/**
 * ViewModel результата анализа: предоставляет выбранную оценку и список
 * наиболее близких по значению Q территорий для сравнения районов.
 */
class EvaluationViewModel(
    historyRepository: HistoryRepository,
    private val evaluationId: String,
) : ViewModel() {

    val state: StateFlow<EvaluationUiState> =
        historyRepository.observeHistory()
            .map { history -> buildState(history) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EvaluationUiState())

    private fun buildState(history: List<AreaEvaluation>): EvaluationUiState {
        val current = history.firstOrNull { it.id == evaluationId } ?: return EvaluationUiState()
        val similar = history
            .filter { it.id != evaluationId }
            .sortedBy { abs(it.score.total - current.score.total) }
            .take(MAX_SIMILAR)
        return EvaluationUiState(evaluation = current, similar = similar)
    }

    private companion object {
        const val MAX_SIMILAR = 3
    }
}
