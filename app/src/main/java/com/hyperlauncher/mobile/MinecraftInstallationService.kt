package com.hyperlauncher.mobile

import java.io.File
import java.util.concurrent.CancellationException

data class MinecraftInstallProgress(
    val completedFiles: Int,
    val totalFiles: Int,
    val completedBytes: Long,
    val totalBytes: Long,
    val currentPath: String?
)

/**
 * Executes the currently supported portion of an install plan.
 *
 * Runs synchronously on the calling thread: callers must use a worker thread.
 * This stage installs the asset index, client JAR, standard libraries, indexed
 * assets, and declared native classifiers. Native archives are extracted into
 * the selected version's private natives directory. Java runtime setup and
 * game launch are not included yet.
 */
object MinecraftInstallationService {
    fun install(
        plan: MinecraftInstallPlan,
        storageRoot: File,
        shouldCancel: (() -> Boolean)? = null,
        onProgress: ((MinecraftInstallProgress) -> Unit)? = null
    ): List<File> {
        val files = buildList {
            add(plan.assetIndexFile)
            add(plan.clientJar)
            addAll(plan.libraries)
            addAll(plan.assets)
            addAll(plan.nativeLibraries)
        }
        val totalBytes = files.fold(0L) { total, file ->
            Math.addExact(total, file.descriptor.size)
        }
        if (shouldCancel?.invoke() == true) {
            throw CancellationException("Minecraft installation cancelled")
        }
        storageRoot.mkdirs()
        MinecraftStoragePreflight.check(storageRoot, totalBytes)
        var completedBytes = 0L
        var completedFiles = 0
        val installed = ArrayList<File>(files.size)

        onProgress?.invoke(
            MinecraftInstallProgress(0, files.size, 0L, totalBytes, files.firstOrNull()?.relativePath)
        )

        for (planned in files) {
            if (shouldCancel?.invoke() == true) {
                throw CancellationException("Minecraft installation cancelled")
            }
            var currentDownloaded = 0L
            val result = VerifiedMinecraftFileDownloader.download(
                descriptor = planned.descriptor,
                relativePath = planned.relativePath,
                storageRoot = storageRoot,
                onProgress = { downloaded, _ ->
                    currentDownloaded = downloaded
                    onProgress?.invoke(
                        MinecraftInstallProgress(
                            completedFiles,
                            files.size,
                            completedBytes + currentDownloaded,
                            totalBytes,
                            planned.relativePath
                        )
                    )
                },
                shouldCancel = shouldCancel
            )
            completedBytes = Math.addExact(completedBytes, planned.descriptor.size)
            completedFiles++
            installed += result
            onProgress?.invoke(
                MinecraftInstallProgress(
                    completedFiles,
                    files.size,
                    completedBytes,
                    totalBytes,
                    files.getOrNull(completedFiles)?.relativePath
                )
            )
        }
        val nativeArchives = plan.nativeLibraries.map { planned ->
            File(storageRoot, planned.relativePath).canonicalFile
        }
        if (nativeArchives.isNotEmpty()) {
            val nativeDirectory = File(storageRoot, "versions/${plan.versionId}/${plan.versionId}-natives")
            installed += MinecraftNativeExtractor.extract(nativeArchives, nativeDirectory, shouldCancel)
        }
        return installed
    }
}
