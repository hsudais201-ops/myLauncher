package com.flame.launcher.content

import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.util.zip.ZipFile

sealed interface MinecraftModpackOverrideStageResult {
    data class Staged(
        val files: List<MinecraftAdditionalInstallFile>,
        val skippedServerFiles: Int,
        val stagingRoot: File
    ) : MinecraftModpackOverrideStageResult
    data class Failed(val reason: String, val cleanupFailures: List<String> = emptyList()) :
        MinecraftModpackOverrideStageResult
}

/** Extracts client overrides to staging only; the shared install transaction commits them. */
class MinecraftModpackOverrideStager(
    private val inspector: MinecraftModpackArchiveInspector = MinecraftModpackArchiveInspector()
) {
    fun stage(
        archive: File,
        inspection: MinecraftModpackArchiveInspector.Inspection,
        contentDirectory: File,
        stagingDirectory: File
    ): MinecraftModpackOverrideStageResult {
        val source = runCatching { archive.canonicalFile }.getOrNull()
            ?: return MinecraftModpackOverrideStageResult.Failed("Invalid modpack archive")
        if (source != inspection.archive.canonicalFile || !source.isFile) {
            return MinecraftModpackOverrideStageResult.Failed("Archive does not match inspection")
        }
        val fresh = when (val result = inspector.inspect(source)) {
            is MinecraftModpackArchiveInspector.Result.Accepted -> result.inspection
            is MinecraftModpackArchiveInspector.Result.Rejected ->
                return MinecraftModpackOverrideStageResult.Failed("Modpack archive is invalid: ${result.reason}")
        }
        if (fresh.archiveSha256 != inspection.archiveSha256 ||
            fresh.manifest != inspection.manifest || fresh.overrideFiles != inspection.overrideFiles
        ) return MinecraftModpackOverrideStageResult.Failed("Modpack archive changed after inspection")

        val root = runCatching { contentDirectory.canonicalFile }.getOrNull()
            ?: return MinecraftModpackOverrideStageResult.Failed("Invalid instance content directory")
        val staging = runCatching { stagingDirectory.canonicalFile }.getOrNull()
            ?: return MinecraftModpackOverrideStageResult.Failed("Invalid staging directory")
        if (!root.isDirectory || !staging.isDirectory ||
            root.toPath().startsWith(staging.toPath()) || staging.toPath().startsWith(root.toPath())
        ) return MinecraftModpackOverrideStageResult.Failed("Instance or staging directory is invalid")

        val entries = inspection.overrideFiles.filter { it.startsWith("overrides/") }
        val skippedServer = inspection.overrideFiles.count { it.startsWith("server-overrides/") }
        if (entries.isEmpty()) return MinecraftModpackOverrideStageResult.Staged(emptyList(), skippedServer, staging)

        val manifestPaths = inspection.manifest.files.map { it.path.lowercase() }.toSet()
        val destinations = HashSet<String>()
        val selected = mutableListOf<Pair<String, String>>()
        for (entryName in entries) {
            val relative = entryName.removePrefix("overrides/")
            val segments = relative.split('/')
            if (relative.isBlank() || relative.length > 1024 || relative.startsWith('/') ||
                relative.contains('\\') || relative.contains('\u0000') ||
                segments.any { it.isBlank() || it == "." || it == ".." }
            ) return MinecraftModpackOverrideStageResult.Failed("Unsafe client override path")
            if (relative.lowercase() in manifestPaths) {
                return MinecraftModpackOverrideStageResult.Failed("Override conflicts with manifest path: $relative")
            }
            if (!destinations.add(relative.lowercase())) {
                return MinecraftModpackOverrideStageResult.Failed("Duplicate client override destination")
            }
            val destination = File(root, relative)
            val parent = runCatching { destination.parentFile.canonicalFile }.getOrNull()
                ?: return MinecraftModpackOverrideStageResult.Failed("Invalid override destination")
            val final = File(parent, destination.name)
            if (!final.toPath().startsWith(root.toPath()) || final == root ||
                destination.name.isBlank() || destination.name == "." || destination.name == ".." ||
                destination.name.any { it == '/' || it == '\\' || it == ':' || it.code < 32 || it.code == 127 } ||
                final.exists()
            ) return MinecraftModpackOverrideStageResult.Failed("Unsafe or existing override destination: $relative")
            selected += relative to entryName
        }

        val tempRoot = try {
            Files.createTempDirectory(staging.toPath(), "flame-overrides-").toFile()
        } catch (_: Exception) {
            return MinecraftModpackOverrideStageResult.Failed("Could not create override staging directory")
        }
        try {
            val stagedFiles = mutableListOf<MinecraftAdditionalInstallFile>()
            var totalBytes = 0L
            ZipFile(source).use { zip ->
                for ((relative, entryName) in selected) {
                    val entry = zip.getEntry(entryName)
                        ?: throw IllegalStateException("Override entry disappeared")
                    if (entry.isDirectory || entry.size < 0L || entry.size > MAX_FILE_BYTES) {
                        throw IllegalStateException("Override entry is not a supported regular file")
                    }
                    val staged = File(tempRoot, relative)
                    if (!staged.parentFile.mkdirs() && !staged.parentFile.isDirectory) {
                        throw IllegalStateException("Could not create override staging path")
                    }
                    zip.getInputStream(entry).use { input ->
                        staged.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var fileBytes = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                fileBytes += count
                                totalBytes += count
                                if (fileBytes > MAX_FILE_BYTES || totalBytes > MAX_TOTAL_BYTES) {
                                    throw IllegalStateException("Client overrides exceed extraction limits")
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    if (staged.length() != entry.size) {
                        throw IllegalStateException("Override size changed during extraction")
                    }
                    val destination = File(root, relative)
                    stagedFiles += MinecraftAdditionalInstallFile(
                        stagedFile = staged,
                        destination = destination,
                        approvedRoot = root,
                        expectedSizeBytes = staged.length(),
                        expectedSha256 = sha256(staged)
                    )
                }
            }
            val after = when (val result = inspector.inspect(source)) {
                is MinecraftModpackArchiveInspector.Result.Accepted -> result.inspection.archiveSha256
                is MinecraftModpackArchiveInspector.Result.Rejected ->
                    throw IllegalStateException("Archive changed during extraction: ${result.reason}")
            }
            if (after != inspection.archiveSha256) throw IllegalStateException("Archive changed during extraction")
            return MinecraftModpackOverrideStageResult.Staged(stagedFiles, skippedServer, tempRoot)
        } catch (error: Exception) {
            val cleanup = if (tempRoot.deleteRecursively()) emptyList() else listOf(tempRoot.path)
            return MinecraftModpackOverrideStageResult.Failed(
                error.message ?: "Could not stage client overrides", cleanup
            )
        }
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_FILE_BYTES = 128L * 1024L * 1024L
        const val MAX_TOTAL_BYTES = 512L * 1024L * 1024L
    }
}
