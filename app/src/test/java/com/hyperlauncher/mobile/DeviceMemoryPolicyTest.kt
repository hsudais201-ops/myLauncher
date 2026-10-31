package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceMemoryPolicyTest {
    private val gib = 1024L * 1024L * 1024L

    @Test
    fun limitsMemoryTargetOnLowRamDevices() {
        val range = DeviceMemoryPolicy.calculate(4L * gib)
        assertEquals(1, range.minimumGb)
        assertEquals(2, range.maximumGb)
        assertEquals(2, range.recommendedGb)
    }

    @Test
    fun scalesTargetForHigherRamDevices() {
        val range = DeviceMemoryPolicy.calculate(8L * gib)
        assertEquals(2, range.minimumGb)
        assertEquals(4, range.maximumGb)
        assertEquals(4, range.recommendedGb)
    }

    @Test
    fun capsVeryLargeDevicesAtEightGigabytes() {
        val range = DeviceMemoryPolicy.calculate(32L * gib)
        assertEquals(8, range.maximumGb)
        assertEquals(4, range.recommendedGb)
    }

    @Test
    fun rejectsUnknownTotalMemory() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceMemoryPolicy.calculate(0L)
        }
    }
}
