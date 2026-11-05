package com.flame.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeCompatibilityTest {
    @Test
    fun arm64DeviceOnMinimumAndroidApiIsCompatible() {
        val report = RuntimeCompatibilityEvaluator.evaluate(
            RuntimeDeviceSnapshot(
                androidApiLevel = 26,
                supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
                totalMemoryBytes = 4L * 1024L * 1024L * 1024L
            )
        )

        assertTrue(report.compatible)
        assertEquals("arm64-v8a", report.primaryRuntimeAbi)
        assertEquals(listOf("arm64-v8a", "armeabi-v7a"), report.supportedDeviceAbis)
        assertTrue(report.issues.isEmpty())
        assertEquals(4L * 1024L * 1024L * 1024L, report.totalMemoryBytes)
    }

    @Test
    fun firstSupportedAbiInDeviceOrderIsSelected() {
        val report = RuntimeCompatibilityEvaluator.evaluate(
            RuntimeDeviceSnapshot(
                androidApiLevel = 35,
                supportedAbis = listOf("riscv64", "x86_64", "arm64-v8a")
            )
        )

        assertEquals("x86_64", report.primaryRuntimeAbi)
        assertEquals(listOf("x86_64", "arm64-v8a"), report.supportedDeviceAbis)
        assertEquals(listOf("riscv64"), report.unsupportedDeviceAbis)
    }

    @Test
    fun oldAndroidApiBlocksRuntimeCompatibility() {
        val report = RuntimeCompatibilityEvaluator.evaluate(
            RuntimeDeviceSnapshot(
                androidApiLevel = 25,
                supportedAbis = listOf("arm64-v8a")
            )
        )

        assertFalse(report.compatible)
        assertEquals(
            listOf(RuntimeCompatibilityIssue.ANDROID_API_TOO_OLD),
            report.issues
        )
    }

    @Test
    fun unsupportedAbiIsReportedAsBlocker() {
        val report = RuntimeCompatibilityEvaluator.evaluate(
            RuntimeDeviceSnapshot(
                androidApiLevel = 35,
                supportedAbis = listOf("riscv64")
            )
        )

        assertFalse(report.compatible)
        assertNull(report.primaryRuntimeAbi)
        assertEquals(
            listOf(RuntimeCompatibilityIssue.NO_SUPPORTED_CPU_ABI),
            report.issues
        )
    }

    @Test
    fun missingMemoryInformationDoesNotBlockCompatibility() {
        val report = RuntimeCompatibilityEvaluator.evaluate(
            RuntimeDeviceSnapshot(
                androidApiLevel = 34,
                supportedAbis = listOf("arm64-v8a"),
                totalMemoryBytes = null
            )
        )

        assertTrue(report.compatible)
        assertNull(report.totalMemoryBytes)
    }

    @Test
    fun nonPositiveMemoryValuesAreDiscarded() {
        val report = RuntimeCompatibilityEvaluator.evaluate(
            RuntimeDeviceSnapshot(
                androidApiLevel = 34,
                supportedAbis = listOf("arm64-v8a"),
                totalMemoryBytes = 0L
            )
        )

        assertTrue(report.compatible)
        assertNull(report.totalMemoryBytes)
    }
}
