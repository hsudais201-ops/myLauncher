package com.mflegend.flamelauncher.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetObjectTrackerTest {
    private val hashA = "a".repeat(40)
    private val hashB = "b".repeat(40)

    @Test fun countsEachUniqueObjectOnlyOnce() {
        val tracker = AssetObjectTracker(maxObjectBytes = 100, maxTotalBytes = 150)

        assertTrue(tracker.register(hashA, 80))
        assertFalse(tracker.register(hashA, 80))
        assertTrue(tracker.register(hashB, 70))
        assertEquals(150L, tracker.totalDeclaredBytes)
    }

    @Test fun rejectsConflictingSizesForDuplicateHashes() {
        val tracker = AssetObjectTracker(maxObjectBytes = 100, maxTotalBytes = 200)
        assertTrue(tracker.register(hashA, 80))

        val error = runCatching { tracker.register(hashA, 81) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(80L, tracker.totalDeclaredBytes)
    }

    @Test fun rejectsAggregateLimitEvenWhenObjectsMayAlreadyExist() {
        val tracker = AssetObjectTracker(maxObjectBytes = 100, maxTotalBytes = 100)
        assertTrue(tracker.register(hashA, 60))

        val error = runCatching { tracker.register(hashB, 41) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(60L, tracker.totalDeclaredBytes)
    }

    @Test fun rejectsInvalidHashesAndOversizedOrMissingSizes() {
        val tracker = AssetObjectTracker(maxObjectBytes = 100, maxTotalBytes = 200)
        assertTrue(runCatching { tracker.register("not-a-hash", 1) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { tracker.register(hashA, -1) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { tracker.register(hashA, 101) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0L, tracker.totalDeclaredBytes)
    }
}
