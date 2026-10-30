package com.hyperlauncher.mobile

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
