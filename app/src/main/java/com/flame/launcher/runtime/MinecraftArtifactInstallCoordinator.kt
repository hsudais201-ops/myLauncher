package com.flame.launcher.runtime

import java.io.File

/**
 * Installs the non-asset files from a Minecraft dependency plan in a
 * low-memory-friendly sequential pass. Asset objects are installed separately
 * after the verified asset-index JSON has been parsed.
 */
class MinecraftArtifactInstallCoordinator(
    private val downloader: MinecraftPlannedArtifactDownloader = MinecraftPlannedArtifactDownloader(),
    private val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES
) {
    init {
        require(maxTotalBytes in 1..ABSOLUTE_MAX_TOTAL_BYTES)
    }

    fun install(
        plan: MinecraftLaunchDependencyPlan,
        gameDirectory: File,
        onProgress: (MinecraftArtifactInstallProgress) -> Unit = {}
    ): List<File> {
        val artifacts = plan.downloads.filter { it.kind != MinecraftPlannedDownloadKind.ASSET }
        require(artifacts.all { it.kind in SUPPORTED_KINDS }) {
            "Minecraft dependency plan contains an unsupported artifact kind."
        }
        require(artifacts.map { it.relativePath }.distinct().size == artifacts.size) {
            "Minecraft dependency plan contains duplicate artifact destinations."
        }
        var totalBytes = 0L
        artifacts.forEach { artifact ->
            val size = artifact.descriptor.size
            require(size > 0L && totalBytes <= maxTotalBytes - size) {
                "Minecraft dependency plan exceeds the configured total artifact limit."
            }
            totalBytes += size
        }

        var completedBytes = 0L
        val installed = ArrayList<File>(artifacts.size)
        artifacts.forEachIndexed { index, artifact ->
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Minecraft artifact installation was cancelled.")
            }
            onProgress(
                MinecraftArtifactInstallProgress(
                    index = index,
                    count = artifacts.size,
                    kind = artifact.kind,
                    relativePath = artifact.relativePath,
                    receivedBytes = 0L,
                    completedBytes = completedBytes,
                    totalBytes = totalBytes
                )
            )
            val file = downloader.download(artifact, gameDirectory) { received, _ ->
                onProgress(
                    MinecraftArtifactInstallProgress(
                        index = index,
                        count = artifacts.size,
                        kind = artifact.kind,
                        relativePath = artifact.relativePath,
                        receivedBytes = received,
                        completedBytes = completedBytes + received,
                        totalBytes = totalBytes
                    )
                )
            }
            installed += file
            completedBytes += artifact.descriptor.size
            onProgress(
                MinecraftArtifactInstallProgress(
                    index = index,
                    count = artifacts.size,
                    kind = artifact.kind,
                    relativePath = artifact.relativePath,
                    receivedBytes = artifact.descriptor.size,
                    completedBytes = completedBytes,
                    totalBytes = totalBytes
                )
            )
        }
        return installed
    }

    companion object {
        const val DEFAULT_MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024
        private const val ABSOLUTE_MAX_TOTAL_BYTES = 16L * 1024 * 1024 * 1024
        private val SUPPORTED_KINDS = setOf(
            MinecraftPlannedDownloadKind.CLIENT,
            MinecraftPlannedDownloadKind.ASSET_INDEX,
            MinecraftPlannedDownloadKind.LIBRARY,
            MinecraftPlannedDownloadKind.NATIVE
        )
    }
}

data class MinecraftArtifactInstallProgress(
    val index: Int,
    val count: Int,
    val kind: MinecraftPlannedDownloadKind,
    val relativePath: String,
    val receivedBytes: Long,
    val completedBytes: Long,
    val totalBytes: Long
)
