package com.example.courseproject.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseproject.domain.model.AreaEvaluation
import com.example.courseproject.domain.model.BoundingBox
import com.example.courseproject.domain.repository.HistoryRepository
import com.example.courseproject.domain.usecase.AnalyzeAreaUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Состояние главного экрана с картой. */
data class MapUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * ViewModel главного экрана: запускает анализ выбранной области и
 * предоставляет историю ранее рассчитанных территорий.
 */
class MapViewModel(
    private val analyzeAreaUseCase: AnalyzeAreaUseCase,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    val history: StateFlow<List<AreaEvaluation>> =
        historyRepository.observeHistory()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _analysisReady = MutableSharedFlow<String>()

    /** Идентификатор завершённого расчёта — сигнал для перехода к экрану результата. */
    val analysisReady: SharedFlow<String> = _analysisReady.asSharedFlow()

    fun analyze(bbox: BoundingBox) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { analyzeAreaUseCase(bbox) }
                .onSuccess { score ->
                    val evaluation = AreaEvaluation(
                        id = UUID.randomUUID().toString(),
                        name = "Область ${history.value.size + 1}",
                        boundingBox = bbox,
                        score = score,
                        timestamp = System.currentTimeMillis(),
                    )
                    historyRepository.save(evaluation)
                    _uiState.update { it.copy(isLoading = false) }
                    _analysisReady.emit(evaluation.id)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Не удалось выполнить анализ области",
                        )
                    }
                }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
