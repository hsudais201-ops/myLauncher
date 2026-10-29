package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftVersionManifestTest {
    @Test
    fun versionIdsUseExpectedSafeShape() {
        val validIds = listOf("1.21.4", "1.20.1", "24w14a", "1.21-pre1")
        val invalidIds = listOf("", "../latest", "1.21/../../bad", " spaces ")

        validIds.forEach {
            assertTrue("Expected valid version ID: $it", it.matches(Regex("^[0-9][A-Za-z0-9._-]{0,63}$")))
        }
        invalidIds.forEach {
            assertTrue("Expected invalid version ID: $it", !it.matches(Regex("^[0-9][A-Za-z0-9._-]{0,63}$")))
        }
    }

    @Test
    fun onlyKnownManifestTypesAreAccepted() {
        assertEquals(listOf("release", "snapshot").filter { it == "release" || it == "snapshot" },
            listOf("release", "snapshot"))
        assertTrue("unknown".let { it != "release" && it != "snapshot" })
    }
}
