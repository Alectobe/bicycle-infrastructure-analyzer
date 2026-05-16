package com.example.courseproject.domain.analysis

import com.example.courseproject.domain.model.CriterionId
import com.example.courseproject.domain.model.OsmData
import com.example.courseproject.domain.model.OsmNode
import com.example.courseproject.domain.model.OsmWay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты алгоритма оценки качества велоинфраструктуры.
 *
 * Узлы в тестовых данных расположены вдоль одного меридиана с шагом 0.01°
 * по широте, поэтому все рёбра дорог имеют одинаковую длину, а ожидаемые
 * значения критериев являются точными рациональными числами.
 */
class BikeInfrastructureScorerTest {

    private val scorer = BikeInfrastructureScorer()

    private fun node(id: Long, lat: Double, lon: Double, tags: Map<String, String> = emptyMap()) =
        OsmNode(id, lat, lon, tags)

    private fun way(id: Long, nodeIds: List<Long>, tags: Map<String, String>) =
        OsmWay(id, nodeIds, tags)

    private fun dataOf(nodes: List<OsmNode>, ways: List<OsmWay>) =
        OsmData(nodes.associateBy { it.id }, ways)

    @Test
    fun safety_isShareOfRoadLengthEquippedWithBikeInfrastructure() {
        val nodes = listOf(
            node(1, 55.00, 37.00), node(2, 55.01, 37.00),
            node(3, 55.00, 37.10), node(4, 55.01, 37.10),
        )
        val ways = listOf(
            way(10, listOf(1, 2), mapOf("highway" to "residential")),
            way(11, listOf(3, 4), mapOf("highway" to "residential", "cycleway" to "lane")),
        )
        val safety = scorer.score(dataOf(nodes, ways)).criterion(CriterionId.SAFETY)
        assertNotNull(safety)
        assertTrue(safety!!.applicable)
        assertEquals(0.5, safety.value, 1e-6)
    }

    @Test
    fun continuity_isShareOfLargestConnectedComponent() {
        // Велодорожки 10 и 11 связаны общим узлом 2; велодорожка 12 изолирована.
        val nodes = listOf(
            node(1, 55.00, 37.00), node(2, 55.01, 37.00), node(3, 55.02, 37.00),
            node(4, 55.00, 37.20), node(5, 55.01, 37.20),
        )
        val cycleway = mapOf("highway" to "cycleway")
        val ways = listOf(
            way(10, listOf(1, 2), cycleway),
            way(11, listOf(2, 3), cycleway),
            way(12, listOf(4, 5), cycleway),
        )
        val continuity = scorer.score(dataOf(nodes, ways)).criterion(CriterionId.CONTINUITY)!!
        assertTrue(continuity.applicable)
        assertEquals(2.0 / 3.0, continuity.value, 1e-6)
    }

    @Test
    fun intersections_isShareOfCrossingsEquippedWithBikeCrossing() {
        val nodes = listOf(
            node(1, 55.00, 37.00),
            node(2, 55.01, 37.00, mapOf("highway" to "crossing", "bicycle" to "yes")),
            node(3, 55.02, 37.00),
            node(4, 55.03, 37.00),
            node(10, 55.01, 37.01),
            node(11, 55.02, 37.01),
        )
        val ways = listOf(
            way(20, listOf(1, 2, 3, 4), mapOf("highway" to "cycleway")),
            way(21, listOf(2, 10), mapOf("highway" to "residential")),
            way(22, listOf(3, 11), mapOf("highway" to "residential")),
        )
        val intersections = scorer.score(dataOf(nodes, ways)).criterion(CriterionId.INTERSECTIONS)!!
        assertTrue(intersections.applicable)
        // Два пересечения, велопереезд организован только на узле 2.
        assertEquals(0.5, intersections.value, 1e-6)
    }

    @Test
    fun highSpeed_consideredOnlyForRoadsAboveSpeedThreshold() {
        val nodes = listOf(
            node(1, 55.00, 37.00), node(2, 55.01, 37.00),
            node(3, 55.00, 37.10), node(4, 55.01, 37.10),
            node(5, 55.00, 37.20), node(6, 55.01, 37.20),
        )
        val ways = listOf(
            way(10, listOf(1, 2), mapOf("highway" to "secondary", "maxspeed" to "60", "cycleway" to "track")),
            way(11, listOf(3, 4), mapOf("highway" to "secondary", "maxspeed" to "60")),
            way(12, listOf(5, 6), mapOf("highway" to "residential", "maxspeed" to "30")),
        )
        val highSpeed = scorer.score(dataOf(nodes, ways)).criterion(CriterionId.HIGH_SPEED)!!
        assertTrue(highSpeed.applicable)
        // Дорога с maxspeed=30 в расчёт не входит; из двух скоростных дорог защищена одна.
        assertEquals(0.5, highSpeed.value, 1e-6)
    }

    @Test
    fun surface_countsOnlyObjectsWithKnownSurfaceTag() {
        val nodes = listOf(
            node(1, 55.00, 37.00), node(2, 55.01, 37.00),
            node(3, 55.00, 37.10), node(4, 55.01, 37.10),
            node(5, 55.00, 37.20), node(6, 55.01, 37.20),
        )
        val ways = listOf(
            way(10, listOf(1, 2), mapOf("highway" to "cycleway", "surface" to "asphalt")),
            way(11, listOf(3, 4), mapOf("highway" to "cycleway", "surface" to "gravel")),
            way(12, listOf(5, 6), mapOf("highway" to "cycleway")),
        )
        val surface = scorer.score(dataOf(nodes, ways)).criterion(CriterionId.SURFACE)!!
        assertTrue(surface.applicable)
        assertEquals(0.5, surface.value, 1e-6)
    }

    @Test
    fun emptyArea_producesZeroScoreWithDataWarning() {
        val result = scorer.score(OsmData.EMPTY)
        assertEquals(0.0, result.total, 1e-9)
        assertNotNull(result.dataWarning)
    }

    @Test
    fun inapplicableCriteria_areExcludedFromWeightedTotal() {
        // Область только с велодорожкой: нет автодорог, скоростных дорог и тегов покрытия.
        val nodes = listOf(node(1, 55.00, 37.00), node(2, 55.01, 37.00))
        val ways = listOf(way(10, listOf(1, 2), mapOf("highway" to "cycleway")))
        val result = scorer.score(dataOf(nodes, ways))

        assertTrue(result.criterion(CriterionId.SAFETY)!!.applicable)
        assertTrue(result.criterion(CriterionId.CONTINUITY)!!.applicable)
        assertFalse(result.criterion(CriterionId.INTERSECTIONS)!!.applicable)
        assertFalse(result.criterion(CriterionId.HIGH_SPEED)!!.applicable)
        assertFalse(result.criterion(CriterionId.SURFACE)!!.applicable)
        // Применимы только S=1 и N=1, веса нормируются — Q = 1.0.
        assertEquals(1.0, result.total, 1e-6)
    }

    @Test
    fun integralScore_combinesAllCriteriaWithModelWeights() {
        // Велодорожка (1-2-3) с асфальтовым покрытием пересекает автодорогу в узле 2;
        // автодорога имеет maxspeed=50 и не оборудована велоинфраструктурой.
        val nodes = listOf(
            node(1, 55.00, 37.00),
            node(2, 55.01, 37.00, mapOf("highway" to "crossing", "bicycle" to "yes")),
            node(3, 55.02, 37.00),
            node(4, 55.02, 37.00),
        )
        val ways = listOf(
            way(10, listOf(1, 2, 3), mapOf("highway" to "cycleway", "surface" to "asphalt")),
            way(11, listOf(2, 4), mapOf("highway" to "residential", "maxspeed" to "50")),
        )
        val result = scorer.score(dataOf(nodes, ways))

        assertEquals(2.0 / 3.0, result.criterion(CriterionId.SAFETY)!!.value, 1e-6)
        assertEquals(1.0, result.criterion(CriterionId.CONTINUITY)!!.value, 1e-6)
        assertEquals(1.0, result.criterion(CriterionId.INTERSECTIONS)!!.value, 1e-6)
        assertEquals(0.0, result.criterion(CriterionId.HIGH_SPEED)!!.value, 1e-6)
        assertEquals(1.0, result.criterion(CriterionId.SURFACE)!!.value, 1e-6)
        // Q = 0.30·(2/3) + 0.25·1 + 0.25·1 + 0.15·0 + 0.05·1 = 0.75.
        assertEquals(0.75, result.total, 1e-6)
    }
}
