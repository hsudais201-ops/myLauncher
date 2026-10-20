package com.voidcraft.launcher.core

import java.io.File

enum class MinecraftArtifactInstallStage {
    PREPARING_METADATA,
    DOWNLOADING_ARTIFACTS,
    COMPLETE
}

fun interface MinecraftInstallPreparer {
    fun prepare(entry: MinecraftVersionEntry): MinecraftInstallPreparation
}

/** Test seam for orchestration; the production implementation is MinecraftInstallExecutor. */
fun interface MinecraftPlanExecutor {
    fun execute(
        plan: MinecraftInstallPlan,
        installRoot: File,
        isCancelled: () -> Boolean = { false },
        onProgress: (InstallArtifactProgress) -> Unit = {}
    ): MinecraftInstallResult
}

data class MinecraftArtifactInstallResult(
    val preparation: MinecraftInstallPreparation,
    val execution: MinecraftInstallResult,
    val installRoot: File
)

/**
 * Coordinates metadata preparation and verified artifact installation. It
 * intentionally stops after installing game artifacts; it does not claim that
 * Java, renderer, authentication, or process-launch prerequisites are ready.
 *
 * Call on a worker thread. Progress callbacks are forwarded from the executor,
 * and cancellation is checked before/after metadata preparation and throughout
 * artifact execution.
 */
class MinecraftArtifactInstallService(
    private val preparer: MinecraftInstallPreparer,
    private val executor: MinecraftPlanExecutor
) {
    fun installArtifacts(
        entry: MinecraftVersionEntry,
        installRoot: File,
        isCancelled: () -> Boolean = { false },
        onStage: (MinecraftArtifactInstallStage) -> Unit = {},
        onProgress: (InstallArtifactProgress) -> Unit = {}
    ): MinecraftArtifactInstallResult {
        checkCancelled(isCancelled)
        onStage(MinecraftArtifactInstallStage.PREPARING_METADATA)
        val preparation = preparer.prepare(entry)
        checkCancelled(isCancelled)

        require(preparation.requestedEntry.id == entry.id) {
            "Prepared metadata does not match the selected version"
        }
        require(preparation.plan.versionId == entry.id) {
            "Install plan does not match the selected version"
        }
        if (preparation.plan.warnings.isNotEmpty()) {
            throw IncompleteInstallPlanException(preparation.plan.warnings)
        }

        onStage(MinecraftArtifactInstallStage.DOWNLOADING_ARTIFACTS)
        val canonicalRoot = installRoot.absoluteFile.canonicalFile
        val result = executor.execute(
            plan = preparation.plan,
            installRoot = canonicalRoot,
            isCancelled = isCancelled,
            onProgress = onProgress
        )
        if (result.versionId != entry.id) {
            throw IllegalStateException("Executor returned a result for a different Minecraft version")
        }
        checkCancelled(isCancelled)
        onStage(MinecraftArtifactInstallStage.COMPLETE)
        return MinecraftArtifactInstallResult(preparation, result, canonicalRoot)
    }

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw DownloadCancelledException()
    }
}
