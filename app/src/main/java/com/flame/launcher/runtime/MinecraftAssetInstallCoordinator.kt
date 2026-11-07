package com.flame.launcher.runtime

import java.io.File

/**
 * Installs all unique objects referenced by a parsed Mojang asset index.
 *
 * Downloads are deliberately sequential to keep memory, socket, and disk
 * pressure predictable on low-resource Android devices. The downloader
 * verifies every object before it is promoted into assets/objects.
 */
class MinecraftAssetInstallCoordinator(
    private val downloader: MinecraftAssetDownloader = MinecraftAssetDownloader(),
    private val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES
) {
    init {
        require(maxTotalBytes in 0..ABSOLUTE_MAX_TOTAL_BYTES)
    }

    fun install(
        index: MinecraftAssetIndex,
        gameDirectory: File,
        onProgress: (MinecraftAssetInstallProgress) -> Unit = {}
    ): List<File> = install(MinecraftAssetDownloadPlanBuilder.build(index), gameDirectory, onProgress)

    fun install(
        plan: List<MinecraftPlannedDownload>,
        gameDirectory: File,
        onProgress: (MinecraftAssetInstallProgress) -> Unit = {}
    ): List<File> {
        require(plan.all { it.kind == MinecraftPlannedDownloadKind.ASSET }) {
            "Minecraft asset install plan contains a non-asset download."
        }
        require(plan.map { it.relativePath }.distinct().size == plan.size) {
            "Minecraft asset install plan contains duplicate destinations."
        }
        var totalBytes = 0L
        plan.forEach { item ->
            val size = item.descriptor.size
            require(size >= 0L && totalBytes <= maxTotalBytes - size) {
                "Minecraft asset index exceeds the configured total download limit."
            }
            totalBytes += size
        }

        var completedBytes = 0L
        val results = ArrayList<File>(plan.size)
        plan.forEachIndexed { index, item ->
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Minecraft asset installation was cancelled.")
            }
            onProgress(
                MinecraftAssetInstallProgress(
                    assetIndex = index,
                    assetCount = plan.size,
                    relativePath = item.relativePath,
                    receivedBytes = 0L,
                    assetSizeBytes = item.descriptor.size,
                    completedBytes = completedBytes,
                    totalBytes = totalBytes
                )
            )
            val file = downloader.download(item, gameDirectory) { received, _ ->
                onProgress(
                    MinecraftAssetInstallProgress(
                        assetIndex = index,
                        assetCount = plan.size,
                        relativePath = item.relativePath,
                        receivedBytes = received,
                        assetSizeBytes = item.descriptor.size,
                        completedBytes = completedBytes + received,
                        totalBytes = totalBytes
                    )
                )
            }
            results += file
            completedBytes += item.descriptor.size
            onProgress(
                MinecraftAssetInstallProgress(
                    assetIndex = index,
                    assetCount = plan.size,
                    relativePath = item.relativePath,
                    receivedBytes = item.descriptor.size,
                    assetSizeBytes = item.descriptor.size,
                    completedBytes = completedBytes,
                    totalBytes = totalBytes
                )
            )
        }
        return results
    }

    companion object {
        const val DEFAULT_MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024
        private const val ABSOLUTE_MAX_TOTAL_BYTES = 16L * 1024 * 1024 * 1024
    }
}

data class MinecraftAssetInstallProgress(
    val assetIndex: Int,
    val assetCount: Int,
    val relativePath: String,
    val receivedBytes: Long,
    val assetSizeBytes: Long,
    val completedBytes: Long,
    val totalBytes: Long
)
