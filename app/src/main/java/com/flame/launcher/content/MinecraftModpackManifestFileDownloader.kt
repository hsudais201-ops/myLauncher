package com.flame.launcher.content

import java.io.File
import java.nio.file.Files

sealed interface MinecraftModpackManifestDownloadResult {
    data class Downloaded(
        val files: List<MinecraftStagedDownloadResult.Downloaded>,
        val skippedClientUnsupported: Int,
        val optionalFilesOmitted: Int
    ) : MinecraftModpackManifestDownloadResult

    data class Failed(
        val relativePath: String,
        val reason: String,
        val stagedFiles: List<File>,
        val cleanupFailures: List<String> = emptyList()
    ) : MinecraftModpackManifestDownloadResult
}

/** Downloads manifest files into isolated staging directories with checksum verification. */
class MinecraftModpackManifestFileDownloader(
    private val downloader: MinecraftContentStagedDownloader = MinecraftContentStagedDownloader()
) {
    fun download(
        plan: MinecraftModpackManifestInstallPlanner.Plan,
        stagingDirectory: File,
        includeOptional: Boolean = false,
        progress: (relativePath: String, downloaded: Long, expected: Long?) -> Unit = { _, _, _ -> }
    ): MinecraftModpackManifestDownloadResult {
        val root = try {
            if (!stagingDirectory.exists() && !stagingDirectory.mkdirs()) {
                return MinecraftModpackManifestDownloadResult.Failed(
                    "", "Could not create modpack download staging directory", emptyList()
                )
            }
            stagingDirectory.canonicalFile.also {
                if (!it.isDirectory || !it.canWrite()) {
                    return MinecraftModpackManifestDownloadResult.Failed(
                        "", "Modpack staging location is not writable", emptyList()
                    )
                }
            }
        } catch (_: Exception) {
            return MinecraftModpackManifestDownloadResult.Failed(
                "", "Invalid modpack staging directory", emptyList()
            )
        }

        val selected = plan.requiredFiles + if (includeOptional) plan.optionalFiles else emptyList()
        val totalBytes = selected.fold(0L) { total, item ->
            val size = item.file.fileSizeBytes
            if (size <= 0L || total > MAX_TOTAL_DOWNLOAD_BYTES - size) {
                return MinecraftModpackManifestDownloadResult.Failed(
                    item.relativePath, "Modpack download exceeds the total size limit", emptyList()
                )
            }
            total + size
        }
        if (totalBytes > MAX_TOTAL_DOWNLOAD_BYTES) {
            return MinecraftModpackManifestDownloadResult.Failed(
                "", "Modpack download exceeds the total size limit", emptyList()
            )
        }
        val completed = mutableListOf<MinecraftStagedDownloadResult.Downloaded>()

        fun cleanup(): List<String> {
            val failures = mutableListOf<String>()
            completed.forEach { item ->
                try {
                    if (item.stagedFile.exists() && !item.stagedFile.delete()) failures += item.stagedFile.path
                    item.stagedFile.parentFile?.let { directory ->
                        if (directory != root && directory.exists() && !directory.delete()) failures += directory.path
                    }
                } catch (_: Exception) {
                    failures += item.stagedFile.path
                }
            }
            return failures
        }

        selected.forEachIndexed { index, planned ->
            val file = planned.file
            val itemDirectory = try {
                Files.createTempDirectory(root.toPath(), "mrpack-$index-").toFile()
            } catch (_: Exception) {
                val failures = cleanup()
                return MinecraftModpackManifestDownloadResult.Failed(
                    planned.relativePath, "Could not create isolated staging area",
                    emptyList(), failures
                )
            }

            if (file.downloads.isEmpty()) {
                val itemCleanupFailures = if (itemDirectory.deleteRecursively()) emptyList()
                    else listOf(itemDirectory.path)
                val failures = cleanup() + itemCleanupFailures
                return MinecraftModpackManifestDownloadResult.Failed(
                    planned.relativePath, "Manifest file has no download URLs", emptyList(), failures
                )
            }

            val install = MinecraftPlannedInstall(
                provider = ContentProviderId.MODRINTH,
                projectId = "modpack-file-$index",
                versionId = "modpack-" + (file.sha1 ?: file.sha512 ?: "unhashed"),
                contentType = MinecraftContentType.MOD,
                action = MinecraftInstallAction.INSTALL_FILE,
                fileName = planned.relativePath.substringAfterLast('/'),
                downloadUrl = file.downloads.first(),
                destinationPath = planned.destination.path,
                expectedSizeBytes = file.fileSizeBytes,
                sha1 = file.sha1,
                sha512 = file.sha512
            )

            var success: MinecraftStagedDownloadResult.Downloaded? = null
            var lastFailure = "No manifest download URL succeeded"
            for (url in file.downloads) {
                when (val result = downloader.download(
                    install.copy(downloadUrl = url),
                    itemDirectory,
                    MinecraftDownloadProgressListener { bytes, expected ->
                        progress(planned.relativePath, bytes, expected)
                    }
                )) {
                    is MinecraftStagedDownloadResult.Downloaded -> {
                        success = result
                        break
                    }
                    is MinecraftStagedDownloadResult.Failed -> lastFailure = result.reason
                }
            }
            if (success == null) {
                val itemCleanupFailures = if (itemDirectory.deleteRecursively()) emptyList()
                    else listOf(itemDirectory.path)
                val failures = cleanup() + itemCleanupFailures
                return MinecraftModpackManifestDownloadResult.Failed(
                    planned.relativePath, lastFailure, emptyList(), failures
                )
            }
            completed += success
        }

        return MinecraftModpackManifestDownloadResult.Downloaded(
            completed, plan.skippedClientUnsupported,
            if (includeOptional) 0 else plan.optionalFiles.size
        )
    }

    private companion object {
        const val MAX_TOTAL_DOWNLOAD_BYTES = 2L * 1024L * 1024L * 1024L
    }
}
