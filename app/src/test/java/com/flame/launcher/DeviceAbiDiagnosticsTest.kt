package com.flame.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAbiDiagnosticsTest {
    @Test
    fun preferredAbiUsesFirstAndroidSupportedAbi() {
        val diagnostics = DeviceAbiDiagnostics(
            supportedAbis = listOf("x86_64", "x86"),
            supported64BitAbis = listOf("x86_64"),
            supported32BitAbis = listOf("x86"),
            processArchitecture = "amd64",
            nativeLibraryDirectory = "/data/app/example/lib/x86_64"
        )

        assertEquals("x86_64", diagnostics.preferredAbi)
    }

    @Test
    fun processArchitectureIsFallbackWhenAndroidAbiListIsEmpty() {
        val diagnostics = DeviceAbiDiagnostics(
            supportedAbis = emptyList(),
            supported64BitAbis = emptyList(),
            supported32BitAbis = emptyList(),
            processArchitecture = "amd64",
            nativeLibraryDirectory = null
        )

        assertEquals("amd64", diagnostics.preferredAbi)
        assertTrue(diagnostics.displayLines().any { it == "Android preferred ABIs: Unavailable" })
        assertTrue(diagnostics.displayLines().any { it == "Native library directory: Unavailable" })
    }

    @Test
    fun diagnosticsShowAllAbiGroupsAndProcessArchitecture() {
        val diagnostics = DeviceAbiDiagnostics(
            supportedAbis = listOf("x86_64", "x86"),
            supported64BitAbis = listOf("x86_64"),
            supported32BitAbis = listOf("x86"),
            processArchitecture = "amd64",
            nativeLibraryDirectory = "/data/app/example/lib/x86_64"
        )

        val lines = diagnostics.displayLines()
        assertTrue(lines.contains("Android preferred ABIs: x86_64, x86"))
        assertTrue(lines.contains("Android 64-bit ABIs: x86_64"))
        assertTrue(lines.contains("Android 32-bit ABIs: x86"))
        assertTrue(lines.contains("App process architecture: amd64"))
        assertTrue(lines.contains("Native library directory: /data/app/example/lib/x86_64"))
    }

    @Test
    fun blankValuesAreReportedAsUnavailableAndDuplicateAbisAreRemoved() {
        val diagnostics = DeviceAbiDiagnostics(
            supportedAbis = listOf("", "x86_64", "x86_64"),
            supported64BitAbis = listOf(" "),
            supported32BitAbis = emptyList(),
            processArchitecture = " ",
            nativeLibraryDirectory = ""
        )

        assertEquals("x86_64", diagnostics.preferredAbi)
        assertTrue(diagnostics.displayLines().contains("Android preferred ABIs: x86_64"))
        assertTrue(diagnostics.displayLines().contains("Android 64-bit ABIs: Unavailable"))
        assertTrue(diagnostics.displayLines().contains("App process architecture: Unavailable"))
    }
}
