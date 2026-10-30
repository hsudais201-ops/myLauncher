package com.hyperlauncher.mobile

import java.io.File

/**
 * Checks the download payload against usable app-storage space before network
 * transfers begin. A small reserve is kept for metadata, temporary files and
 * filesystem overhead; native extraction may require additional space.
 */
object MinecraftStoragePreflight {
    const val MINIMUM_RESERVE_BYTES = 32L * 1024L * 1024L

    fun check(
        storageRoot: File,
        requiredDownloadBytes: Long,
        availableBytes: Long = storageRoot.usableSpace,
        reserveBytes: Long = MINIMUM_RESERVE_BYTES
    ) {
        require(requiredDownloadBytes >= 0L) { "Required storage size cannot be negative" }
        require(availableBytes >= 0L && reserveBytes >= 0L) { "Storage values cannot be negative" }
        val requiredWithReserve = Math.addExact(requiredDownloadBytes, reserveBytes)
        if (availableBytes < requiredWithReserve) {
            val missing = requiredWithReserve - availableBytes
            val missingMiB = (missing + 1024L * 1024L - 1L) / (1024L * 1024L)
            throw IllegalStateException(
                "Not enough free storage. Free at least $missingMiB MiB, then try again."
            )
        }
    }
}
