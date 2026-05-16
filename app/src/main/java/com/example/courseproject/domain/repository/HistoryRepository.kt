package com.example.courseproject.domain.repository

import com.example.courseproject.domain.model.AreaEvaluation
import kotlinx.coroutines.flow.Flow

/** Хранилище истории рассчитанных оценок территорий. */
interface HistoryRepository {

    /** Поток истории расчётов, отсортированный от новых записей к старым. */
    fun observeHistory(): Flow<List<AreaEvaluation>>

    suspend fun save(evaluation: AreaEvaluation)

    suspend fun getById(id: String): AreaEvaluation?

    suspend fun delete(id: String)
}
