package com.example.courseproject.domain.repository

import com.example.courseproject.domain.model.AreaEvaluation
import kotlinx.coroutines.flow.Flow

/**
 * Хранилище истории рассчитанных оценок территорий.
 *
 * Реализуется в data-слое (файловое хранилище с сериализацией в JSON).
 * Поток observeHistory() позволяет ViewModel-ям подписаться на изменения
 * и автоматически обновлять список истории при появлении новых записей.
 */
interface HistoryRepository {

    /** Поток истории расчётов, отсортированный от новых записей к старым. */
    fun observeHistory(): Flow<List<AreaEvaluation>>

    /** Сохраняет (или обновляет, если id уже есть) запись в истории. */
    suspend fun save(evaluation: AreaEvaluation)

    /** Возвращает запись по идентификатору, либо null, если её нет. */
    suspend fun getById(id: String): AreaEvaluation?

    /** Удаляет запись по идентификатору. */
    suspend fun delete(id: String)
}
