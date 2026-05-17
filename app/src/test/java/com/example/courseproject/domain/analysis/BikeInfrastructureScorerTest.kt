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
 * Узлы тестовых данных расположены так, чтобы рёбра дорог имели одинаковую
 * длину, поэтому ожидаемые значения критериев — точные рациональные числа.
 */
class BikeInfrastructureScorerTest {

    private val scorer = BikeInfrastructureScorer()

    private fun node(id: Long, lat: Double, lon: Double, tags: Map<String, String> = emptyMap()) =
        OsmNode(id, lat, lon, tags)

    private fun way(id: Long, nodeIds: List<Long>, tags: Map<String, String>) =
        OsmWay(id, nodeIds, tags)

    private fun area(nodes: List<OsmNode>, ways: List<OsmWay>) =
        OsmData(nodes.associateBy { it.id }, ways)

    @Test
    fun safety_isShareOfLowStressNetwork() {
        // Велодорожка (низкострессовая) и обычная улица 60 км/ч равной длины.
        val nodes = listOf(
            node(1, 55.00, 37.00), node(2, 55.01, 37.00),
            node(3, 55.00, 37.20), node(4, 55.01, 37.20),
        )
        val ways = listOf(
            way(10, listOf(1, 2), mapOf("highway" to "cycleway")),
            way(11, listOf(3, 4), mapOf("highway" to "residential", "maxspeed" to "60")),
        )
        val safety = scorer.score(area(nodes, ways)).criterion(CriterionId.SAFETY)
        assertNotNull(safety)
        assertTrue(safety!!.applicable)
        assertEquals(0.5, safety.value, 1e-6)
    }

    @Test
    fun safety_treatsCalmStreetAsLowStress() {
        // Спокойная улица 30 км/ч засчитывается как низкострессовая, быстрая (60) — нет.
        val nodes = listOf(
            node(1, 55.00, 37.00), node(2, 55.01, 37.00),
            node(3, 55.00, 37.20), node(4, 55.01, 37.20),
        )
        val ways = listOf(
            way(10, listOf(1, 2), mapOf("highway" to "residential", "maxspeed" to "30")),
            way(11, listOf(3, 4), mapOf("highway" to "residential", "maxspeed" to "60")),
        )
        val safety = scorer.score(area(nodes, ways)).criterion(CriterionId.SAFETY)!!
        assertEquals(0.5, safety.value, 1e-6)
    }

    @Test
    fun parallelCycleway_protectsAdjacentRoad() {
        // Велодорожка и быстрая дорога 60 км/ч идут параллельно на расстоянии ~11 м.
        val nodes = listOf(
            node(1, 55.0000, 37.000), node(2, 55.0000, 37.005), node(3, 55.0000, 37.010),
            node(4, 55.0001, 37.000), node(5, 55.0001, 37.005), node(6, 55.0001, 37.010),
        )
        val ways = listOf(
            way(10, listOf(1, 2, 3), mapOf("highway" to "cycleway")),
            way(11, listOf(4, 5, 6), mapOf("highway" to "residential", "maxspeed" to "60")),
        )
        val result = scorer.score(area(nodes, ways))
        // Дорога защищена параллельной велодорожкой — вся сеть низкострессовая.
        assertEquals(1.0, result.criterion(CriterionId.SAFETY)!!.value, 1e-6)
        // И эта скоростная дорога считается обеспеченной велоинфраструктурой.
        val highSpeed = result.criterion(CriterionId.HIGH_SPEED)!!
        assertTrue(highSpeed.applicable)
        assertEquals(1.0, highSpeed.value, 1e-6)
    }

    @Test
    fun continuity_isShareOfLargestConnectedComponent() {
        // Велодорожки 10 и 11 связаны общим узлом 2; велодорожка 12 изолирована.
        val nodes = listOf(
            node(1, 55.00, 37.00), node(2, 55.01, 37.00), node(3, 55.02, 37.00),
            node(4, 55.00, 37.30), node(5, 55.01, 37.30),
        )
        val cycleway = mapOf("highway" to "cycleway")
        val ways = listOf(
            way(10, listOf(1, 2), cycleway),
            way(11, listOf(2, 3), cycleway),
            way(12, listOf(4, 5), cycleway),
        )
        val continuity = scorer.score(area(nodes, ways)).criterion(CriterionId.CONTINUITY)!!
        assertTrue(continuity.applicable)
        assertEquals(2.0 / 3.0, continuity.value, 1e-6)
    }

    @Test
    fun intersections_isShareOfOrganizedCrossings() {
        val nodes = listOf(
            node(1, 55.00, 37.00), node(2, 55.01, 37.00),
            node(10, 55.00, 38.00, mapOf("highway" to "crossing", "crossing" to "traffic_signals")),
            node(11, 55.01, 38.00, mapOf("highway" to "crossing", "crossing" to "marked")),
            node(12, 55.02, 38.00, mapOf("highway" to "crossing", "crossing" to "uncontrolled")),
            node(13, 55.03, 38.00, mapOf("highway" to "crossing", "crossing" to "unmarked")),
        )
        val ways = listOf(way(10, listOf(1, 2), mapOf("highway" to "cycleway")))
        val intersections = scorer.score(area(nodes, ways)).criterion(CriterionId.INTERSECTIONS)!!
        assertTrue(intersections.applicable)
        // Из четырёх перекрёстков организованы два (светофор и разметка).
        assertEquals(0.5, intersections.value, 1e-6)
    }

    @Test
    fun highSpeed_isShareOfProtectedFastRoads() {
        val nodes = listOf(
            node(1, 55.00, 37.00), node(2, 55.01, 37.00),
            node(3, 55.00, 37.30), node(4, 55.01, 37.30),
        )
        val ways = listOf(
            way(10, listOf(1, 2), mapOf("highway" to "secondary", "maxspeed" to "60", "cycleway" to "track")),
            way(11, listOf(3, 4), mapOf("highway" to "secondary", "maxspeed" to "60")),
        )
        val highSpeed = scorer.score(area(nodes, ways)).criterion(CriterionId.HIGH_SPEED)!!
        assertTrue(highSpeed.applicable)
        assertEquals(0.5, highSpeed.value, 1e-6)
    }

    @Test
    fun surface_countsOnlyInfrastructureWithKnownSurface() {
        val nodes = listOf(
            node(1, 55.00, 37.00), node(2, 55.01, 37.00),
            node(3, 55.00, 37.30), node(4, 55.01, 37.30),
            node(5, 55.00, 37.60), node(6, 55.01, 37.60),
        )
        val ways = listOf(
            way(10, listOf(1, 2), mapOf("highway" to "cycleway", "surface" to "asphalt")),
            way(11, listOf(3, 4), mapOf("highway" to "cycleway", "surface" to "gravel")),
            way(12, listOf(5, 6), mapOf("highway" to "cycleway")),
        )
        val surface = scorer.score(area(nodes, ways)).criterion(CriterionId.SURFACE)!!
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
        // Область только с велодорожкой: нет перекрёстков, скоростных дорог и тегов покрытия.
        val nodes = listOf(node(1, 55.00, 37.00), node(2, 55.01, 37.00))
        val ways = listOf(way(10, listOf(1, 2), mapOf("highway" to "cycleway")))
        val result = scorer.score(area(nodes, ways))

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
        // Велодорожка 10 (асфальт) и быстрая улица 11 (60 км/ч, без велоинфраструктуры).
        val nodes = listOf(
            node(1, 55.00, 37.00), node(2, 55.01, 37.00), node(3, 55.02, 37.00),
            node(4, 55.00, 37.50), node(5, 55.01, 37.50),
            node(10, 55.00, 38.00, mapOf("highway" to "crossing", "crossing" to "traffic_signals")),
            node(11, 55.01, 38.00, mapOf("highway" to "crossing", "crossing" to "uncontrolled")),
        )
        val ways = listOf(
            way(10, listOf(1, 2, 3), mapOf("highway" to "cycleway", "surface" to "asphalt")),
            way(11, listOf(4, 5), mapOf("highway" to "residential", "maxspeed" to "60")),
        )
        val result = scorer.score(area(nodes, ways))

        assertEquals(2.0 / 3.0, result.criterion(CriterionId.SAFETY)!!.value, 1e-6)
        assertEquals(1.0, result.criterion(CriterionId.CONTINUITY)!!.value, 1e-6)
        assertEquals(0.5, result.criterion(CriterionId.INTERSECTIONS)!!.value, 1e-6)
        assertEquals(0.0, result.criterion(CriterionId.HIGH_SPEED)!!.value, 1e-6)
        assertEquals(1.0, result.criterion(CriterionId.SURFACE)!!.value, 1e-6)
        // Q = 0.30·(2/3) + 0.25·1 + 0.25·0.5 + 0.15·0 + 0.05·1 = 0.625.
        assertEquals(0.625, result.total, 1e-6)
    }
}
