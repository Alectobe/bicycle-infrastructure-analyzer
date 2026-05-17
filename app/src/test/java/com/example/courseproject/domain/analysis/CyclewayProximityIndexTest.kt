package com.example.courseproject.domain.analysis

import com.example.courseproject.domain.model.OsmNode
import com.example.courseproject.domain.model.OsmWay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты пространственного индекса параллельных велодорожек. */
class CyclewayProximityIndexTest {

    // Велодорожка вдоль широты 55.0000; параллельная дорога — на ~11 м севернее.
    private val nodes = mapOf(
        1L to OsmNode(1, 55.0000, 37.000),
        2L to OsmNode(2, 55.0000, 37.005),
        3L to OsmNode(3, 55.0000, 37.010),
        4L to OsmNode(4, 55.0001, 37.000),
        5L to OsmNode(5, 55.0001, 37.005),
        6L to OsmNode(6, 55.0001, 37.010),
        7L to OsmNode(7, 56.0000, 38.000),
        8L to OsmNode(8, 56.0000, 38.010),
    )
    private val cycleway = OsmWay(10, listOf(1, 2, 3), mapOf("highway" to "cycleway"))
    private val index = CyclewayProximityIndex(listOf(cycleway), nodes)

    @Test
    fun parallelRoad_isFullyCovered() {
        val parallelRoad = OsmWay(20, listOf(4, 5, 6), mapOf("highway" to "residential"))
        assertTrue(index.cyclewayCoverage(parallelRoad) >= 0.5)
    }

    @Test
    fun distantRoad_hasNoCoverage() {
        val distantRoad = OsmWay(21, listOf(7, 8), mapOf("highway" to "residential"))
        assertEquals(0.0, index.cyclewayCoverage(distantRoad), 1e-9)
    }

    @Test
    fun emptyIndex_reportsNoCoverage() {
        val emptyIndex = CyclewayProximityIndex(emptyList(), nodes)
        val road = OsmWay(22, listOf(4, 5, 6), mapOf("highway" to "residential"))
        assertEquals(0.0, emptyIndex.cyclewayCoverage(road), 1e-9)
    }
}
