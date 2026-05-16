package com.example.courseproject.data.repository

import com.example.courseproject.domain.model.AreaEvaluation
import com.example.courseproject.domain.repository.HistoryRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Реализация [HistoryRepository] с сохранением истории расчётов в JSON-файл
 * внутреннего хранилища приложения. Актуальное состояние публикуется через [Flow].
 */
class HistoryRepositoryImpl(
    filesDir: File,
    private val gson: Gson = Gson(),
) : HistoryRepository {

    private val storageFile = File(filesDir, "history.json")
    private val state = MutableStateFlow(readFromDisk())

    override fun observeHistory(): Flow<List<AreaEvaluation>> = state.asStateFlow()

    override suspend fun save(evaluation: AreaEvaluation) {
        state.update { current ->
            (listOf(evaluation) + current.filterNot { it.id == evaluation.id })
                .sortedByDescending { it.timestamp }
        }
        writeToDisk()
    }

    override suspend fun getById(id: String): AreaEvaluation? =
        state.value.firstOrNull { it.id == id }

    override suspend fun delete(id: String) {
        state.update { current -> current.filterNot { it.id == id } }
        writeToDisk()
    }

    private fun readFromDisk(): List<AreaEvaluation> {
        if (!storageFile.exists()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<AreaEvaluation>>() {}.type
            gson.fromJson<List<AreaEvaluation>>(storageFile.readText(), type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun writeToDisk() {
        runCatching { storageFile.writeText(gson.toJson(state.value)) }
    }
}
