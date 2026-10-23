package com.voidcraft.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureStoreDownloadPolicyTest {
    private val reserve = 16L * 1024L * 1024L
    private val mib = 1024L * 1024L

    @Test
    fun leavesAReserveWhenCalculatingMaximumDownloadSize() {
        assertEquals(0L, maxFeaturePackageBytesForAvailableSpace(0L))
        assertEquals(0L, maxFeaturePackageBytesForAvailableSpace(reserve - 1L))
        assertEquals(0L, maxFeaturePackageBytesForAvailableSpace(reserve))
        assertEquals(4L * mib, maxFeaturePackageBytesForAvailableSpace(reserve + 4L * mib))
    }

    @Test
    fun capsLargeFreeSpaceAtThePackageLimit() {
        assertEquals(500L * mib, maxFeaturePackageBytesForAvailableSpace(2L * 1024L * mib))
    }

    @Test
    fun validatesDeclaredAndUnknownContentLengthsAgainstAvailableSpace() {
        assertFalse(featurePackageContentLengthFits(-1L, 0L))
        assertFalse(featurePackageContentLengthFits(-2L, 100L))
        assertTrue(featurePackageContentLengthFits(-1L, 100L))
        assertFalse(featurePackageContentLengthFits(0L, 100L))
        assertTrue(featurePackageContentLengthFits(100L, 100L))
        assertFalse(featurePackageContentLengthFits(101L, 100L))
    }
}
