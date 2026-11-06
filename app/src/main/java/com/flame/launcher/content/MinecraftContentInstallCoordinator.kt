package com.flame.launcher.content

import java.io.File

sealed interface MinecraftContentInstallCoordinatorResult {
    data class Installed(
        val plan: MinecraftContentInstallPlan,
        val manifest: MinecraftContentInstallManifest,
        val skippedUnsupportedFiles: Int = 0,
        val omittedOptionalFiles: Int = 0,
        val skippedServerOverrideFiles: Int = 0
    ) : MinecraftContentInstallCoordinatorResult

    data class Failed(
        val reason: String,
        val cleanupFailures: List<String> = emptyList()
    ) : MinecraftContentInstallCoordinatorResult
}

/**
 * Orchestrates dependency resolution, safe planning, verified staging, transactional
 * installation, and durable ownership recording. Call from a worker/IO dispatcher.
 *
 * Project lookup is injected because each provider has its own project-detail API.
 * No instance path is inferred: the caller must supply the selected instance target.
 */
class MinecraftContentInstallCoordinator(
    private val manifestStore: MinecraftContentInstallManifestStore,
    private val downloader: MinecraftContentStagedDownloader = MinecraftContentStagedDownloader(),
    private val transaction: MinecraftContentInstallTransaction = MinecraftContentInstallTransaction(),
    private val modpackInspector: MinecraftModpackArchiveInspector = MinecraftModpackArchiveInspector(),
    private val modpackManifestDownloader: MinecraftModpackManifestFileDownloader = MinecraftModpackManifestFileDownloader(),
    private val modpackOverrideStager: MinecraftModpackOverrideStager = MinecraftModpackOverrideStager()
) {
    fun install(
        selectedItems: List<MinecraftContentInstallItem>,
        target: MinecraftInstanceTarget,
        stagingDirectory: File,
        modpackStagingDirectory: File,
        findVersion: (ContentProviderId, String, String?) -> MinecraftContentVersion?,
        findProject: (ContentProviderId, String) -> MinecraftContentProject?,
        progress: (projectId: String, downloaded: Long, expected: Long?) -> Unit = { _, _, _ -> },
        includeOptionalModpackFiles: Boolean = false
    ): MinecraftContentInstallCoordinatorResult {
        if (selectedItems.isEmpty()) return failed("Select at least one content version")

        val roots = selectedItems.map { it.version }
        val resolved = when (val result = MinecraftContentDependencyResolver.resolve(roots, findVersion)) {
            is MinecraftContentDependencyResolver.Result.Resolved -> result.installOrder
            is MinecraftContentDependencyResolver.Result.Failed ->
                return failed("Dependency resolution failed: ${result.reason}")
        }

        val projectsByKey = selectedItems.associate {
            MinecraftContentDependencyResolver.ProjectKey(it.project.provider, it.project.projectId) to it.project
        }.toMutableMap()
        val installItems = mutableListOf<MinecraftContentInstallItem>()
        for (version in resolved) {
            val key = MinecraftContentDependencyResolver.ProjectKey(version.provider, version.projectId)
            val project = projectsByKey[key] ?: try {
                findProject(version.provider, version.projectId)
            } catch (error: Exception) {
                null
            } ?: return failed("Could not load project details for required dependency ${version.projectId}")
            if (project.provider != version.provider || project.projectId != version.projectId) {
                return failed("Project details did not match required dependency ${version.projectId}")
            }
            projectsByKey[key] = project
            installItems += MinecraftContentInstallItem(project, version)
        }

        val plan = when (val result = MinecraftContentInstallPlanner.plan(
            installItems,
            target,
            modpackStagingDirectory.canonicalPath
        )) {
            is MinecraftInstallPlanResult.Ready -> result.plan
            is MinecraftInstallPlanResult.Rejected ->
                return failed("Install plan rejected: ${result.reasons.joinToString("; ")}")
        }

        val downloaded = mutableListOf<MinecraftStagedDownloadResult.Downloaded>()
        fun cleanup(): List<String> = downloaded.mapNotNull { item ->
            try {
                if (item.stagedFile.exists() && !item.stagedFile.delete()) {
                    "Could not remove staged file ${item.stagedFile.name}"
                } else null
            } catch (_: Exception) {
                "Could not remove staged file ${item.stagedFile.name}"
            }
        }

        for (item in plan.items) {
            val result = downloader.download(
                item,
                stagingDirectory,
                MinecraftDownloadProgressListener { bytes, expected ->
                    progress(item.projectId, bytes, expected)
                }
            )
            when (result) {
                is MinecraftStagedDownloadResult.Downloaded -> downloaded += result
                is MinecraftStagedDownloadResult.Failed -> {
                    val cleanupFailures = cleanup()
                    return MinecraftContentInstallCoordinatorResult.Failed(
                        "Download failed for ${item.projectId}: ${result.reason}",
                        cleanupFailures
                    )
                }
            }
        }

        val modpackInspections = mutableListOf<Pair<MinecraftStagedDownloadResult.Downloaded, MinecraftModpackArchiveInspector.Inspection>>()
        for (item in downloaded.filter { it.install.contentType == MinecraftContentType.MODPACK }) {
            when (val inspection = modpackInspector.inspect(item.stagedFile)) {
                is MinecraftModpackArchiveInspector.Result.Accepted ->
                    modpackInspections += item to inspection.inspection
                is MinecraftModpackArchiveInspector.Result.Rejected -> {
                    val cleanupFailures = cleanup()
                    return MinecraftContentInstallCoordinatorResult.Failed(
                        "Modpack archive rejected for ${item.install.projectId}: ${inspection.reason}",
                        cleanupFailures
                    )
                }
            }
        }

        val additionalFiles = mutableListOf<MinecraftAdditionalInstallFile>()
        val extraStagingRoots = mutableListOf<File>()
        var skippedUnsupportedFiles = 0
        var omittedOptionalFiles = 0
        var skippedServerOverrideFiles = 0
        fun cleanupAdditional(): List<String> {
            val failures = mutableListOf<String>()
            additionalFiles.forEach { item ->
                try {
                    if (item.stagedFile.exists() && !item.stagedFile.delete()) failures += item.stagedFile.path
                    item.stagedFile.parentFile?.let { parent ->
                        if (parent != stagingDirectory && parent.isDirectory) parent.delete()
                    }
                } catch (_: Exception) { failures += item.stagedFile.path }
            }
            extraStagingRoots.forEach { root ->
                try { if (root.exists() && !root.deleteRecursively()) failures += root.path }
                catch (_: Exception) { failures += root.path }
            }
            return failures.distinct()
        }

        for ((archive, inspection) in modpackInspections) {
            val manifestPlan = when (val planned = MinecraftModpackManifestInstallPlanner().plan(
                inspection.manifest, target, inspection.overrideFiles
            )) {
                is MinecraftModpackManifestInstallPlanner.Result.Ready -> planned.plan
                is MinecraftModpackManifestInstallPlanner.Result.Rejected -> {
                    val failures = cleanup() + cleanupAdditional()
                    return MinecraftContentInstallCoordinatorResult.Failed(
                        "Modpack compatibility check failed for ${archive.install.projectId}: ${planned.reasons.joinToString("; ")}",
                        failures
                    )
                }
            }
            when (val remote = modpackManifestDownloader.download(
                manifestPlan,
                stagingDirectory,
                includeOptional = includeOptionalModpackFiles,
                progress = { relativePath, bytes, expected ->
                    progress("${archive.install.projectId}:$relativePath", bytes, expected)
                }
            )) {
                is MinecraftModpackManifestDownloadResult.Downloaded -> {
                    skippedUnsupportedFiles += remote.skippedClientUnsupported
                    omittedOptionalFiles += remote.optionalFilesOmitted
                    remote.files.forEach { file ->
                        additionalFiles += MinecraftAdditionalInstallFile(
                            stagedFile = file.stagedFile,
                            destination = File(file.install.destinationPath),
                            approvedRoot = File(target.contentDirectory),
                            expectedSizeBytes = file.stagedFile.length(),
                            expectedSha256 = sha256(file.stagedFile)
                        )
                    }
                }
                is MinecraftModpackManifestDownloadResult.Failed -> {
                    val failures = cleanup() + cleanupAdditional() + remote.cleanupFailures
                    return MinecraftContentInstallCoordinatorResult.Failed(
                        "Modpack file download failed at ${remote.relativePath}: ${remote.reason}",
                        failures
                    )
                }
            }
            when (val overrides = modpackOverrideStager.stage(
                archive.stagedFile, inspection, File(target.contentDirectory), stagingDirectory
            )) {
                is MinecraftModpackOverrideStageResult.Staged -> {
                    additionalFiles += overrides.files
                    skippedServerOverrideFiles += overrides.skippedServerFiles
                    if (overrides.files.isNotEmpty()) extraStagingRoots += overrides.stagingRoot
                }
                is MinecraftModpackOverrideStageResult.Failed -> {
                    val failures = cleanup() + cleanupAdditional() + overrides.cleanupFailures
                    return MinecraftContentInstallCoordinatorResult.Failed(
                        "Modpack override staging failed: ${overrides.reason}", failures
                    )
                }
            }
        }

        // Preserve the expected bytes before staging cleanup so a failed ownership write
        // can safely compensate without deleting a file that another process changed.
        val rollbackDigests = buildMap {
            downloaded.forEach { item ->
                put(File(item.install.destinationPath).canonicalPath, sha256(item.stagedFile))
            }
            additionalFiles.forEach { item ->
                put(item.destination.canonicalPath, item.expectedSha256)
            }
        }

        val committed = transaction.install(
            plan = plan,
            stagedDownloads = downloaded,
            stagingDirectory = stagingDirectory,
            appPrivateStagingDirectory = modpackStagingDirectory,
            additionalFiles = additionalFiles
        )
        val additionalCleanupFailures = cleanupAdditional()
        if (committed !is MinecraftContentInstallTransactionResult.Installed) {
            val reason = (committed as MinecraftContentInstallTransactionResult.Failed).reason
            val cleanupFailures = cleanup() + additionalCleanupFailures
            return MinecraftContentInstallCoordinatorResult.Failed(
                "Install transaction failed: $reason",
                cleanupFailures
            )
        }

        val additionalEntries = additionalFiles.map { file ->
            val destination = file.destination.canonicalFile
            MinecraftManagedContentInstall(
                provider = file.provider,
                projectId = file.projectId,
                versionId = file.versionId,
                contentType = file.contentType,
                fileName = destination.name,
                installedPath = destination.path,
                sha256 = sha256(destination),
                expectedSizeBytes = destination.length()
            )
        }
        val saved = manifestStore.recordSuccessfulInstall(plan, committed, additionalEntries)
        val cleanupFailures = cleanup() + additionalCleanupFailures
        return when (saved) {
            is MinecraftContentInstallManifestResult.Saved ->
                MinecraftContentInstallCoordinatorResult.Installed(
                plan = plan,
                manifest = saved.manifest,
                skippedUnsupportedFiles = skippedUnsupportedFiles,
                omittedOptionalFiles = omittedOptionalFiles,
                skippedServerOverrideFiles = skippedServerOverrideFiles
            )
            is MinecraftContentInstallManifestResult.Failed -> {
                val rollbackFailures = transaction.rollbackInstalled(committed.installedPaths, rollbackDigests)
                MinecraftContentInstallCoordinatorResult.Failed(
                    if (rollbackFailures.isEmpty()) {
                        "Managed-content manifest could not be saved; installed files were rolled back: ${saved.reason}"
                    } else {
                        "Managed-content manifest could not be saved; some installed files could not be safely rolled back: ${saved.reason}"
                    },
                    cleanupFailures + rollbackFailures
                )
            }
            is MinecraftContentInstallManifestResult.Loaded -> {
                val rollbackFailures = transaction.rollbackInstalled(committed.installedPaths, rollbackDigests)
                MinecraftContentInstallCoordinatorResult.Failed(
                    if (rollbackFailures.isEmpty()) "Unexpected manifest persistence result; installed files were rolled back"
                    else "Unexpected manifest persistence result; some installed files could not be safely rolled back",
                    cleanupFailures + rollbackFailures
                )
            }
        }
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun failed(reason: String) =
        MinecraftContentInstallCoordinatorResult.Failed(reason)
}
