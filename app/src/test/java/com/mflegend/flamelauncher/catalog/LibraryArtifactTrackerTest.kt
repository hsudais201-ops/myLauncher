package com.mflegend.flamelauncher.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryArtifactTrackerTest {
    private val shaA = "a".repeat(40)
    private val shaB = "b".repeat(40)

    @Test fun countsUniqueDestinationsOnlyOnce() {
        val tracker = LibraryArtifactTracker(maxArtifactBytes = 100, maxTotalBytes = 150)

        assertTrue(tracker.register("org/example/a.jar", 80, shaA))
        assertFalse(tracker.register("org/example/a.jar", 80, shaA))
        assertTrue(tracker.register("org/example/b.jar", 70, shaB))
        assertEquals(150L, tracker.totalDeclaredBytes)
    }

    @Test fun rejectsConflictingHashOrSizeAtSameDestination() {
        val tracker = LibraryArtifactTracker(maxArtifactBytes = 100, maxTotalBytes = 200)
        assertTrue(tracker.register("org/example/a.jar", 80, shaA))

        assertTrue(runCatching {
            tracker.register("org/example/a.jar", 80, shaB)
        }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching {
            tracker.register("org/example/a.jar", 81, shaA)
        }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(80L, tracker.totalDeclaredBytes)
    }

    @Test fun enforcesAggregateLimitAcrossUniqueArtifacts() {
        val tracker = LibraryArtifactTracker(maxArtifactBytes = 100, maxTotalBytes = 100)
        assertTrue(tracker.register("a.jar", 60, shaA))

        assertTrue(runCatching {
            tracker.register("b.jar", 41, shaB)
        }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(60L, tracker.totalDeclaredBytes)
    }

    @Test fun rejectsInvalidSizesAndHashes() {
        val tracker = LibraryArtifactTracker(maxArtifactBytes = 100, maxTotalBytes = 200)
        assertTrue(runCatching { tracker.register("a.jar", 0, shaA) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { tracker.register("a.jar", 101, shaA) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { tracker.register("a.jar", 1, "bad") }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0L, tracker.totalDeclaredBytes)
    }
}
