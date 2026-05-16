package com.example.courseproject.domain.usecase

import com.example.courseproject.domain.analysis.BikeInfrastructureScorer
import com.example.courseproject.domain.model.BoundingBox
import com.example.courseproject.domain.model.OsmData
import com.example.courseproject.domain.model.OsmNode
import com.example.courseproject.domain.model.OsmWay
import com.example.courseproject.domain.repository.OsmRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты сценария анализа территории с подменным репозиторием данных OSM. */
class AnalyzeAreaUseCaseTest {

    private class FakeOsmRepository(private val data: OsmData) : OsmRepository {
        var lastForceRefresh: Boolean? = null

        override suspend fun loadOsmData(bbox: BoundingBox, forceRefresh: Boolean): OsmData {
            lastForceRefresh = forceRefresh
            return data
        }
    }

    private val bbox = BoundingBox(south = 55.0, west = 37.0, north = 55.1, east = 37.1)

    @Test
    fun invoke_scoresDataReturnedByRepository() = runTest {
        val data = OsmData(
            nodes = mapOf(
                1L to OsmNode(1, 55.00, 37.00),
                2L to OsmNode(2, 55.01, 37.00),
            ),
            ways = listOf(OsmWay(10, listOf(1, 2), mapOf("highway" to "cycleway"))),
        )
        val useCase = AnalyzeAreaUseCase(FakeOsmRepository(data), BikeInfrastructureScorer())

        val score = useCase(bbox)

        assertEquals(5, score.criteria.size)
        assertTrue(score.total > 0.0)
    }

    @Test
    fun invoke_forwardsForceRefreshFlagToRepository() = runTest {
        val repository = FakeOsmRepository(OsmData.EMPTY)
        val useCase = AnalyzeAreaUseCase(repository, BikeInfrastructureScorer())

        useCase(bbox, forceRefresh = true)

        assertEquals(true, repository.lastForceRefresh)
    }
}
