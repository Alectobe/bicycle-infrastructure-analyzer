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
    val evaluation: AreaEvaluation? = null,                // выбранная оценка (null — запись не найдена)
    val similar: List<AreaEvaluation> = emptyList(),       // близкие по Q территории для сравнения
)

/**
 * ViewModel результата анализа: предоставляет выбранную оценку и список
 * наиболее близких по значению Q территорий для сравнения районов.
 *
 * Используется одной ViewModel сразу для двух экранов — Result и Detail.
 * Параметр evaluationId передаётся при создании ViewModel через фабрику
 * (см. ResultRoute / DetailRoute) — он определяет, какую запись из истории показывать.
 */
class EvaluationViewModel(
    historyRepository: HistoryRepository,       // источник истории — здесь только читаем
    private val evaluationId: String,           // id отображаемой записи
) : ViewModel() {

    /**
     * Состояние экрана. Реактивно собирается из потока истории:
     *   – подписываемся на observeHistory();
     *   – на каждое изменение списка собираем EvaluationUiState (запись + похожие);
     *   – stateIn превращает результат в горячий StateFlow для UI.
     */
    val state: StateFlow<EvaluationUiState> =
        historyRepository.observeHistory()
            .map { history -> buildState(history) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EvaluationUiState())

    /** Собирает состояние экрана по текущему списку истории. */
    private fun buildState(history: List<AreaEvaluation>): EvaluationUiState {
        // Ищем выбранную запись по id. Если её нет (была удалена) — возвращаем пустое состояние.
        val current = history.firstOrNull { it.id == evaluationId } ?: return EvaluationUiState()
        // Похожие районы — берём остаток истории и сортируем по близости Q к текущему.
        // abs(Δ) = модуль разницы значений Q. Чем меньше — тем «ближе» район.
        val similar = history
            .filter { it.id != evaluationId }                              // саму себя исключаем
            .sortedBy { abs(it.score.total - current.score.total) }        // сортируем по близости Q
            .take(MAX_SIMILAR)                                              // оставляем N ближайших
        return EvaluationUiState(evaluation = current, similar = similar)
    }

    private companion object {
        /** Сколько похожих районов показывать в секции «Похожие районы». */
        const val MAX_SIMILAR = 3
    }
}
