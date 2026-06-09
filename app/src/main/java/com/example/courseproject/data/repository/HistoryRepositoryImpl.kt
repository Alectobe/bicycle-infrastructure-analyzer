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
 *
 * Двойной слой хранения:
 *   – StateFlow держит актуальный список в памяти — ViewModel подписываются на него
 *     и моментально получают обновления при любом изменении (save / delete);
 *   – JSON-файл хранит данные на диске, чтобы история переживала перезапуск приложения.
 */
class HistoryRepositoryImpl(
    filesDir: File,                              // корень внутреннего хранилища приложения
    private val gson: Gson = Gson(),             // парсер JSON для сериализации списка
) : HistoryRepository {

    // Файл, в котором лежит сериализованный список оценок.
    private val storageFile = File(filesDir, "history.json")
    // In-memory копия списка, обновляется на каждое save/delete и пересылается подписчикам.
    // При создании репозитория читаем актуальное состояние с диска.
    private val state = MutableStateFlow(readFromDisk())

    /** Возвращает StateFlow только-для-чтения — подписчики не могут менять состояние напрямую. */
    override fun observeHistory(): Flow<List<AreaEvaluation>> = state.asStateFlow()

    override suspend fun save(evaluation: AreaEvaluation) {
        // Атомарно обновляем StateFlow:
        //   1) кладём новую запись в начало списка;
        //   2) удаляем из остатка старую запись с тем же id (если такая была — это update-by-id);
        //   3) сортируем по убыванию timestamp, чтобы самые свежие оказались сверху.
        state.update { current ->
            (listOf(evaluation) + current.filterNot { it.id == evaluation.id })
                .sortedByDescending { it.timestamp }
        }
        // Сразу записываем актуальный список на диск, чтобы он сохранился между запусками.
        writeToDisk()
    }

    override suspend fun getById(id: String): AreaEvaluation? =
        // Простой поиск по id в текущем in-memory списке.
        state.value.firstOrNull { it.id == id }

    override suspend fun delete(id: String) {
        // Убираем запись с указанным id из in-memory списка…
        state.update { current -> current.filterNot { it.id == id } }
        // …и сразу сохраняем на диск, чтобы удаление было постоянным.
        writeToDisk()
    }

    /** Чтение списка истории с диска. Возвращает пустой список, если файла нет или JSON битый. */
    private fun readFromDisk(): List<AreaEvaluation> {
        // Если файла истории ещё нет — это первый запуск приложения, возвращаем пустой список.
        if (!storageFile.exists()) return emptyList()
        // runCatching — try/catch как выражение. Если что-то падает (битый JSON, IO-ошибка) —
        // .getOrDefault() возвращает запасное значение, и приложение не падает.
        return runCatching {
            // TypeToken нужен, чтобы Gson «вспомнил» параметризованный тип List<AreaEvaluation>:
            // в рантайме сам параметр-тип стирается, и без TypeToken Gson не знал бы, что десериализовать.
            val type = object : TypeToken<List<AreaEvaluation>>() {}.type
            gson.fromJson<List<AreaEvaluation>>(storageFile.readText(), type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** Запись текущего состояния на диск. Тихо игнорирует ошибки IO (например, нет места). */
    private fun writeToDisk() {
        runCatching { storageFile.writeText(gson.toJson(state.value)) }
    }
}
