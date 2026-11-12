package com.flame.launcher.runtime

import java.io.File
import java.security.MessageDigest
import java.nio.file.Files

/**
 * Verifies that every planned client, asset-index, library, native archive,
 * and asset object is already present in an instance directory.
 *
 * This is an integrity check only. It does not prove native extraction,
 * JVM execution, authentication, or renderer compatibility.
 */
class MinecraftInstalledArtifactsValidator {
    fun validate(
        gameDirectory: File,
        dependencyPlan: MinecraftLaunchDependencyPlan,
        assetPlan: List<MinecraftPlannedDownload> = emptyList()
    ): MinecraftInstalledArtifactsValidation {
        if (!gameDirectory.exists() || !gameDirectory.isDirectory ||
            Files.isSymbolicLink(gameDirectory.toPath())
        ) {
            return MinecraftInstalledArtifactsValidation.InvalidGameDirectory
        }

        val planned = dependencyPlan.downloads.filter {
            it.kind != MinecraftPlannedDownloadKind.ASSET
        } + assetPlan

        if (assetPlan.any { it.kind != MinecraftPlannedDownloadKind.ASSET } ||
            planned.map { it.relativePath }.distinct().size != planned.size
        ) {
            return MinecraftInstalledArtifactsValidation.InvalidPlan
        }

        val root = runCatching { gameDirectory.canonicalFile }.getOrNull()
            ?: return MinecraftInstalledArtifactsValidation.InvalidGameDirectory
        if (root != gameDirectory.absoluteFile) {
            return MinecraftInstalledArtifactsValidation.InvalidGameDirectory
        }

        val missing = ArrayList<String>()
        val corrupt = ArrayList<String>()
        planned.forEach { item ->
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Minecraft installed-artifact validation was cancelled.")
            }
            val file = resolveSafeFile(root, item.relativePath)
            if (file == null || !file.isFile) {
                missing += item.relativePath
            } else {
                val digestMatches = if (file.length() != item.descriptor.size) {
                    false
                } else {
                    try {
                        sha1(file) == item.descriptor.sha1.lowercase()
                    } catch (cancelled: InterruptedException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                }
                if (!digestMatches) corrupt += item.relativePath
            }
        }

        return if (missing.isEmpty() && corrupt.isEmpty()) {
            MinecraftInstalledArtifactsValidation.Complete(
                verifiedFileCount = planned.size,
                verifiedBytes = planned.sumOf { it.descriptor.size }
            )
        } else {
            MinecraftInstalledArtifactsValidation.Incomplete(
                missingFiles = missing,
                corruptFiles = corrupt
            )
        }
    }

    private fun resolveSafeFile(root: File, relativePath: String): File? {
        if (relativePath.isBlank() || relativePath.startsWith('/') ||
            '\\' in relativePath || ':' in relativePath ||
            relativePath.split('/').any { it.isBlank() || it == "." || it == ".." }
        ) return null

        var cursor = root
        for (segment in relativePath.split('/')) {
            cursor = File(cursor, segment)
            if (Files.isSymbolicLink(cursor.toPath())) return null
            if (cursor.exists() && cursor.canonicalFile != cursor.absoluteFile) return null
        }
        if (!cursor.toPath().normalize().startsWith(root.toPath())) return null
        return cursor
    }

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException("Minecraft installed-artifact validation was cancelled.")
                }
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val BUFFER_SIZE = 32 * 1024
    }
}

sealed interface MinecraftInstalledArtifactsValidation {
    data class Complete(
        val verifiedFileCount: Int,
        val verifiedBytes: Long
    ) : MinecraftInstalledArtifactsValidation

    data class Incomplete(
        val missingFiles: List<String>,
        val corruptFiles: List<String>
    ) : MinecraftInstalledArtifactsValidation

    data object InvalidGameDirectory : MinecraftInstalledArtifactsValidation
    data object InvalidPlan : MinecraftInstalledArtifactsValidation
}
