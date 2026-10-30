package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class MinecraftStoragePreflightTest {
    @Test
    fun acceptsPayloadWhenReserveAlsoFits() {
        MinecraftStoragePreflight.check(
            storageRoot = File("build/test-launcher-storage"),
            requiredDownloadBytes = 100L,
            availableBytes = 150L,
            reserveBytes = 50L
        )
    }

    @Test
    fun rejectsPayloadWhenFreeSpaceIsBelowPayloadPlusReserve() {
        assertThrows(IllegalStateException::class.java) {
            MinecraftStoragePreflight.check(
                storageRoot = File("build/test-launcher-storage"),
                requiredDownloadBytes = 101L,
                availableBytes = 150L,
                reserveBytes = 50L
            )
        }
    }

    @Test
    fun includesAdditionalExtractionSpaceInPreflight() {
        assertThrows(IllegalStateException::class.java) {
            MinecraftStoragePreflight.check(
                storageRoot = File("build/test-launcher-storage"),
                requiredDownloadBytes = 100L,
                availableBytes = 150L,
                reserveBytes = 50L,
                additionalRequiredBytes = 1L
            )
        }
    }

    @Test
    fun roundsVeryLargeStorageShortfallWithoutOverflow() {
        val error = assertThrows(IllegalStateException::class.java) {
            MinecraftStoragePreflight.check(
                storageRoot = File("build/test-launcher-storage"),
                requiredDownloadBytes = Long.MAX_VALUE,
                availableBytes = 0L,
                reserveBytes = 0L
            )
        }
        assertEquals(
            "Not enough free storage. Free at least 8796093022208 MiB, then try again.",
            error.message
        )
    }

    @Test
    fun rejectsOverflowInRequiredStorageCalculation() {
        assertThrows(ArithmeticException::class.java) {
            MinecraftStoragePreflight.check(
                storageRoot = File("build/test-launcher-storage"),
                requiredDownloadBytes = Long.MAX_VALUE,
                availableBytes = Long.MAX_VALUE,
                reserveBytes = 1L
            )
        }
    }
}
