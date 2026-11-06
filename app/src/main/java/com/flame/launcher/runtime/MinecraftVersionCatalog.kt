package com.flame.launcher.runtime

import java.net.HttpURLConnection
import java.net.URL

/**
 * Coordinates verified metadata retrieval and parsing for the runtime layer.
 *
 * This class performs blocking I/O through MinecraftMetadataDownloader. Call
 * loadManifest/loadVersionDetails only from a worker thread.
 */
class MinecraftVersionCatalog(
    connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    connectTimeoutMs: Int = 15_000,
    readTimeoutMs: Int = 15_000,
    manifestMaxBytes: Int = 4 * 1024 * 1024,
    detailsMaxBytes: Int = 8 * 1024 * 1024
) {
    private val downloader = MinecraftMetadataDownloader(
        connectionFactory = connectionFactory,
        connectTimeoutMs = connectTimeoutMs,
        readTimeoutMs = readTimeoutMs,
        manifestMaxBytes = manifestMaxBytes,
        detailsMaxBytes = detailsMaxBytes
    )

    /** Fetches and validates Mojang's current version catalog. */
    fun loadManifest(): MinecraftVersionManifest =
        MinecraftVersionManifest.parse(downloader.downloadManifest())

    /** Fetches, SHA-1 verifies, then parses metadata for a manifest entry. */
    fun loadVersionDetails(entry: MinecraftVersionEntry): MinecraftVersionDetails =
        MinecraftVersionDetails.parse(downloader.downloadVersionDetails(entry), entry.id)
}
