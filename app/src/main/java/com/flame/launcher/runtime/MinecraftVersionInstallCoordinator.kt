package com.flame.launcher.runtime

import java.io.File

/**
 * End-to-end installation pipeline for one Mojang version:
 * verified version metadata -> dependency plan -> client/libraries/native
 * archives -> guarded native extraction -> asset-index parse -> asset objects.
 *
 * All work is blocking I/O and must run on a worker thread. Cancellation is
 * cooperative through thread interruption and is checked by each stage.
 */
class MinecraftVersionInstallCoordinator(
    private val catalog: MinecraftVersionCatalog = MinecraftVersionCatalog(),
    private val nativeInstaller: MinecraftNativeInstallCoordinator = MinecraftNativeInstallCoordinator(),
    private val assetInstaller: MinecraftAssetInstallCoordinator = MinecraftAssetInstallCoordinator()
) {
    fun install(
        entry: MinecraftVersionEntry,
        environment: MinecraftLibraryEnvironment,
        gameDirectory: File,
        onProgress: (MinecraftVersionInstallProgress) -> Unit = {}
    ): MinecraftVersionInstallResult {
        checkNotCancelled()
        onProgress(MinecraftVersionInstallProgress(MinecraftVersionInstallStage.METADATA, "Loading verified version metadata"))
        val details = catalog.loadVersionDetails(entry)
        checkNotCancelled()
        val plan = MinecraftLaunchDependencyPlanBuilder.build(details, environment)
        onProgress(MinecraftVersionInstallProgress(MinecraftVersionInstallStage.PLANNING, "Dependency plan created"))

        val nativeDirectory = File(gameDirectory, "versions/${details.id}/${details.id}-natives")
        val nativeResult = nativeInstaller.install(plan, gameDirectory, nativeDirectory) { progress ->
            onProgress(
                MinecraftVersionInstallProgress(
                    stage = MinecraftVersionInstallStage.ARTIFACTS,
                    message = "Downloading ${progress.kind.name.lowercase()}: ${progress.relativePath}",
                    completedBytes = progress.completedBytes,
                    totalBytes = progress.totalBytes
                )
            )
        }
        checkNotCancelled()

        val assetIndexDownload = plan.downloads.singleOrNull {
            it.kind == MinecraftPlannedDownloadKind.ASSET_INDEX
        } ?: throw IllegalArgumentException("Minecraft version plan has no unique asset index.")
        val assetIndexFile = File(gameDirectory, assetIndexDownload.relativePath)
        require(assetIndexFile.isFile && assetIndexFile.length() in 1..MAX_ASSET_INDEX_BYTES) {
            "Verified Minecraft asset index is missing or exceeds its parsing limit."
        }
        onProgress(MinecraftVersionInstallProgress(MinecraftVersionInstallStage.ASSET_INDEX, "Parsing asset index"))
        val assetIndex = MinecraftAssetIndex.parse(
            assetIndexFile.readText(Charsets.UTF_8),
            details.assets
        )
        checkNotCancelled()
        val assetPlan = MinecraftAssetDownloadPlanBuilder.build(assetIndex)
        val assetFiles = assetInstaller.install(assetPlan, gameDirectory) { progress ->
            onProgress(
                MinecraftVersionInstallProgress(
                    stage = MinecraftVersionInstallStage.ASSETS,
                    message = "Downloading asset ${progress.assetIndex + 1}/${progress.assetCount}",
                    completedBytes = progress.completedBytes,
                    totalBytes = progress.totalBytes
                )
            )
        }
        checkNotCancelled()
        onProgress(MinecraftVersionInstallProgress(MinecraftVersionInstallStage.COMPLETE, "Minecraft files installed"))
        return MinecraftVersionInstallResult(
            details = details,
            dependencyPlan = plan,
            installedArtifacts = nativeResult.installedArtifacts,
            extractedNativeFiles = nativeResult.extractedNativeFiles,
            assetFiles = assetFiles
        )
    }

    private fun checkNotCancelled() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("Minecraft version installation was cancelled.")
        }
    }

    companion object {
        private const val MAX_ASSET_INDEX_BYTES = 16L * 1024 * 1024
    }
}

enum class MinecraftVersionInstallStage {
    METADATA,
    PLANNING,
    ARTIFACTS,
    ASSET_INDEX,
    ASSETS,
    COMPLETE
}

data class MinecraftVersionInstallProgress(
    val stage: MinecraftVersionInstallStage,
    val message: String,
    val completedBytes: Long = 0L,
    val totalBytes: Long? = null
)

data class MinecraftVersionInstallResult(
    val details: MinecraftVersionDetails,
    val dependencyPlan: MinecraftLaunchDependencyPlan,
    val installedArtifacts: List<File>,
    val extractedNativeFiles: List<File>,
    val assetFiles: List<File>
)
