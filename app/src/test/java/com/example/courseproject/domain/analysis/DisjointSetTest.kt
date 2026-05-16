package com.example.courseproject.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Тесты системы непересекающихся множеств, используемой для анализа связности. */
class DisjointSetTest {

    @Test
    fun newElement_isConnectedOnlyToItself() {
        val dsu = DisjointSet()
        dsu.add(1L)
        dsu.add(2L)
        assertTrue(dsu.connected(1L, 1L))
        assertFalse(dsu.connected(1L, 2L))
    }

    @Test
    fun union_connectsElementsTransitively() {
        val dsu = DisjointSet()
        dsu.union(1L, 2L)
        dsu.union(2L, 3L)
        assertTrue(dsu.connected(1L, 3L))
    }

    @Test
    fun separateGroups_remainDisconnectedUntilJoined() {
        val dsu = DisjointSet()
        dsu.union(10L, 20L)
        dsu.union(30L, 40L)
        assertFalse(dsu.connected(10L, 30L))

        dsu.union(20L, 30L)
        assertTrue(dsu.connected(10L, 40L))
    }

    @Test
    fun find_returnsSameRepresentativeForOneGroupAndDifferentForAnother() {
        val dsu = DisjointSet()
        dsu.union(1L, 2L)
        dsu.union(3L, 4L)
        assertEquals(dsu.find(1L), dsu.find(2L))
        assertEquals(dsu.find(3L), dsu.find(4L))
        assertFalse(dsu.find(1L) == dsu.find(3L))
    }
}
