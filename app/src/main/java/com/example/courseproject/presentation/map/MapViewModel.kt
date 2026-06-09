package com.example.courseproject.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseproject.domain.model.AnalysisError
import com.example.courseproject.domain.model.AnalysisException
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

/**
 * Состояние главного экрана с картой.
 *
 * Ошибка хранится в виде типизированного [AnalysisError]; локализованный
 * текст по нему формирует presentation-слой через ресурсы строк.
 */
data class MapUiState(
    val isLoading: Boolean = false,      // идёт ли сейчас запрос/расчёт
    val error: AnalysisError? = null,    // типизированная ошибка анализа; null — ошибок нет
)

/**
 * ViewModel главного экрана: запускает анализ выбранной области и
 * предоставляет историю ранее рассчитанных территорий.
 *
 * Это реализация шаблона MVVM (Model–View–ViewModel). ViewModel хранит
 * состояние экрана (uiState), запускает бизнес-операции (analyze) и слушает
 * источники данных (история через репозиторий). View (Compose-экран) только
 * подписывается на состояние и вызывает методы ViewModel — обратной связи нет.
 *
 * Использует Kotlin Coroutines для асинхронности: viewModelScope автоматически
 * отменяет фоновые операции при уничтожении ViewModel (например, когда пользователь
 * закрывает экран и его процесс завершается).
 */
class MapViewModel(
    private val analyzeAreaUseCase: AnalyzeAreaUseCase,    // сценарий «Оценить территорию»
    private val historyRepository: HistoryRepository,      // хранилище истории расчётов
) : ViewModel() {

    /**
     * История расчётов в виде «горячего» потока: подписывается на репозиторий
     * через observeHistory() и публикует список текущим интересующимся читателям.
     *
     * stateIn преобразует холодный Flow в StateFlow с начальным значением (emptyList).
     * SharingStarted.WhileSubscribed(5_000) — подписка на источник держится, пока
     * у StateFlow есть подписчики, плюс 5 секунд после ухода последнего из них
     * (это сглаживает быстрые перерисовки экрана при поворотах устройства).
     */
    val history: StateFlow<List<AreaEvaluation>> =
        historyRepository.observeHistory()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Изменяемое состояние UI хранится приватно — снаружи доступно только чтение.
    private val _uiState = MutableStateFlow(MapUiState())

    /** Состояние UI, доступное Compose-экрану для подписки. */
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // Канал одноразовых событий «расчёт готов» с идентификатором результата.
    // SharedFlow вместо StateFlow — потому что это событие, а не состояние:
    // при повторной подписке прошлые события не нужны.
    private val _analysisReady = MutableSharedFlow<String>()

    /** Идентификатор завершённого расчёта — сигнал для перехода к экрану результата. */
    val analysisReady: SharedFlow<String> = _analysisReady.asSharedFlow()

    /**
     * Запускает анализ выбранной области. Вызывается из MapScreen при нажатии
     * кнопки «Анализировать».
     */
    fun analyze(bbox: BoundingBox) {
        // Анти-дребезг: если предыдущий запрос ещё не завершён, игнорируем повторный.
        if (_uiState.value.isLoading) return
        // viewModelScope.launch — запускаем корутину, привязанную к жизни ViewModel.
        // Когда ViewModel будет уничтожена, корутина автоматически отменится.
        viewModelScope.launch {
            // Сразу переводим UI в состояние «загрузка», сбрасываем прошлую ошибку.
            _uiState.update { it.copy(isLoading = true, error = null) }
            // runCatching ловит любые исключения из use case и возвращает Result<T>.
            runCatching { analyzeAreaUseCase(bbox) }
                .onSuccess { score ->
                    // Успех: упаковываем результат в AreaEvaluation и сохраняем в историю.
                    val evaluation = AreaEvaluation(
                        id = UUID.randomUUID().toString(),           // уникальный id записи
                        index = history.value.size + 1,              // порядковый номер для имени «Область N»
                        boundingBox = bbox,
                        score = score,
                        timestamp = System.currentTimeMillis(),      // время сохранения
                    )
                    historyRepository.save(evaluation)
                    // Сбрасываем флаг загрузки и шлём событие «готово» с id результата.
                    _uiState.update { it.copy(isLoading = false) }
                    _analysisReady.emit(evaluation.id)
                }
                .onFailure { throwable ->
                    // Неудача: переводим UI в состояние «ошибка» с типизированной причиной.
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = toAnalysisError(throwable),
                        )
                    }
                }
        }
    }

    /** Сброс состояния ошибки — вызывается, когда пользователь закрывает диалог ошибки. */
    fun dismissError() = _uiState.update { it.copy(error = null) }

    /**
     * Преобразование произвольного исключения в типизированную ошибку анализа.
     * Если это наш [AnalysisException] — берём типизированную причину из него,
     * иначе классифицируем как [AnalysisError.Unknown].
     */
    private fun toAnalysisError(throwable: Throwable): AnalysisError =
        if (throwable is AnalysisException) throwable.error else AnalysisError.Unknown
}
