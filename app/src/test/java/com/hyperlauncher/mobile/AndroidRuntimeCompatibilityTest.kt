package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRuntimeCompatibilityTest {
    @Test
    fun prefersArm64AndRequiresAnAndroidRuntime() {
        val result = AndroidRuntimeCompatibility.assess(
            androidApiLevel = 35,
            supportedAbis = listOf("armeabi-v7a", "arm64-v8a", "x86_64"),
            runtimeInstalled = false
        )
        assertEquals("arm64-v8a", result.selectedAbi)
        assertFalse(result.runtimeUsable)
        assertTrue(result.warning!!.contains("Android-compatible Java runtime"))
    }

    @Test
    fun acceptsInstalledRuntimeOnSupportedX64Android() {
        val result = AndroidRuntimeCompatibility.assess(
            androidApiLevel = 35,
            supportedAbis = listOf("x86_64"),
            runtimeInstalled = true
        )
        assertTrue(result.runtimeUsable)
        assertNull(result.warning)
    }

    @Test
    fun rejectsUnsupportedAbi() {
        val result = AndroidRuntimeCompatibility.assess(
            androidApiLevel = 35,
            supportedAbis = listOf("armeabi-v7a"),
            runtimeInstalled = true
        )
        assertFalse(result.runtimeUsable)
        assertTrue(result.warning!!.contains("No supported Android runtime ABI"))
    }

    @Test
    fun rejectsAndroidVersionsBelowTheAppMinimum() {
        val result = AndroidRuntimeCompatibility.assess(
            androidApiLevel = 25,
            supportedAbis = listOf("arm64-v8a"),
            runtimeInstalled = true
        )
        assertFalse(result.runtimeUsable)
        assertTrue(result.warning!!.contains("Android 8.0"))
    }
}
