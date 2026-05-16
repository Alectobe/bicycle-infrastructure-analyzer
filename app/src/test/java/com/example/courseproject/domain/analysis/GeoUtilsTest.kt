package com.example.courseproject.domain.analysis

import com.example.courseproject.domain.model.OsmNode
import com.example.courseproject.domain.model.OsmWay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты геометрических расчётов на сфере. */
class GeoUtilsTest {

    @Test
    fun haversine_betweenSamePoint_isZero() {
        assertEquals(0.0, GeoUtils.haversineMeters(55.0, 37.0, 55.0, 37.0), 1e-6)
    }

    @Test
    fun haversine_oneDegreeOfLatitude_isAboutMeridianArcLength() {
        // Длина дуги в 1° по меридиану составляет приблизительно 111.2 км.
        val distance = GeoUtils.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_195.0, distance, 500.0)
    }

    @Test
    fun haversine_isSymmetric() {
        val forward = GeoUtils.haversineMeters(55.75, 37.62, 55.76, 37.64)
        val backward = GeoUtils.haversineMeters(55.76, 37.64, 55.75, 37.62)
        assertEquals(forward, backward, 1e-6)
        assertTrue(forward > 0.0)
    }

    @Test
    fun wayLength_isSumOfConsecutiveSegments() {
        val nodes = mapOf(
            1L to OsmNode(1, 55.00, 37.00),
            2L to OsmNode(2, 55.01, 37.00),
            3L to OsmNode(3, 55.02, 37.00),
        )
        val way = OsmWay(100, listOf(1, 2, 3), emptyMap())
        val singleSegment = GeoUtils.haversineMeters(55.00, 37.00, 55.01, 37.00)
        assertEquals(2 * singleSegment, GeoUtils.wayLengthMeters(way, nodes), 1e-6)
    }

    @Test
    fun wayLength_skipsSegmentsWithMissingNodes() {
        val nodes = mapOf(
            1L to OsmNode(1, 55.00, 37.00),
            3L to OsmNode(3, 55.02, 37.00),
        )
        // Узел 2 отсутствует, поэтому оба смежных с ним сегмента пропускаются.
        val way = OsmWay(100, listOf(1, 2, 3), emptyMap())
        assertEquals(0.0, GeoUtils.wayLengthMeters(way, nodes), 1e-6)
    }
}
