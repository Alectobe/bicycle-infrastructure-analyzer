package com.example.courseproject.domain.analysis

import com.example.courseproject.domain.model.OsmNode
import com.example.courseproject.domain.model.OsmWay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты классификации объектов OpenStreetMap по тегам. */
class OsmTagsTest {

    private fun way(tags: Map<String, String>) = OsmWay(1, emptyList(), tags)
    private fun node(tags: Map<String, String>) = OsmNode(1, 55.0, 37.0, tags)

    @Test
    fun maxSpeed_parsesNumericValues() {
        assertEquals(60, OsmTags.maxSpeedKmh(way(mapOf("maxspeed" to "60"))))
        assertEquals(30, OsmTags.maxSpeedKmh(way(mapOf("maxspeed" to "30"))))
    }

    @Test
    fun maxSpeed_parsesZonalValues() {
        // RU:urban — российская городская зона (60 км/ч).
        assertEquals(60, OsmTags.maxSpeedKmh(way(mapOf("maxspeed" to "RU:urban"))))
        assertEquals(20, OsmTags.maxSpeedKmh(way(mapOf("maxspeed" to "RU:living_street"))))
    }

    @Test
    fun maxSpeed_convertsMilesPerHour() {
        assertEquals(48, OsmTags.maxSpeedKmh(way(mapOf("maxspeed" to "30 mph"))))
    }

    @Test
    fun maxSpeed_isNullWhenTagAbsent() {
        assertNull(OsmTags.maxSpeedKmh(way(emptyMap())))
    }

    @Test
    fun lowStressHighway_recognisesCarFreeAndCalmTypes() {
        assertTrue(OsmTags.isLowStressHighway(way(mapOf("highway" to "cycleway"))))
        assertTrue(OsmTags.isLowStressHighway(way(mapOf("highway" to "living_street"))))
        assertFalse(OsmTags.isLowStressHighway(way(mapOf("highway" to "primary"))))
    }

    @Test
    fun ownBikeInfrastructure_detectsCyclewayAndBikeLane() {
        assertTrue(OsmTags.hasOwnBikeInfrastructure(way(mapOf("highway" to "cycleway"))))
        assertTrue(OsmTags.hasOwnBikeInfrastructure(
            way(mapOf("highway" to "secondary", "cycleway" to "track"))))
        assertFalse(OsmTags.hasOwnBikeInfrastructure(way(mapOf("highway" to "secondary"))))
    }

    @Test
    fun organizedCrossing_requiresControlOrMarking() {
        assertTrue(OsmTags.isOrganizedCrossing(
            node(mapOf("highway" to "crossing", "crossing" to "traffic_signals"))))
        assertFalse(OsmTags.isOrganizedCrossing(
            node(mapOf("highway" to "crossing", "crossing" to "uncontrolled"))))
        assertFalse(OsmTags.isOrganizedCrossing(node(mapOf("highway" to "crossing"))))
    }
}
