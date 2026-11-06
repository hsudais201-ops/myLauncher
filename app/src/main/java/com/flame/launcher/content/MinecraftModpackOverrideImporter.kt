package com.flame.launcher.content

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

sealed interface MinecraftModpackOverrideImportResult {
    data class Imported(val installedPaths: List<String>, val skippedServerFiles: Int) :
        MinecraftModpackOverrideImportResult
    data class Failed(val reason: String, val rollbackFailures: List<String> = emptyList()) :
        MinecraftModpackOverrideImportResult
}

/**
 * Imports only client-side overrides/ from a previously inspected .mrpack archive.
 * Remote manifest files are intentionally handled by the verified downloader pipeline;
 * server-overrides/ are never copied into a client instance.
 *
 * All bytes are extracted to a private temporary directory first. Existing destinations
 * are rejected, and a failed commit removes only files created by this import.
 */
class MinecraftModpackOverrideImporter {
    fun import(
        archive: File,
        inspection: MinecraftModpackArchiveInspector.Inspection,
        contentDirectory: File,
        stagingDirectory: File
    ): MinecraftModpackOverrideImportResult {
        val archiveFile = runCatching { archive.canonicalFile }.getOrNull()
            ?: return failed("Invalid modpack archive path")
        if (archiveFile != inspection.archive.canonicalFile || !archiveFile.isFile) {
            return failed("Modpack archive does not match the inspected archive")
        }
        val root = runCatching { contentDirectory.canonicalFile }.getOrNull()
            ?: return failed("Invalid instance content directory")
        val staging = runCatching { stagingDirectory.canonicalFile }.getOrNull()
            ?: return failed("Invalid private staging directory")
        if (!root.isDirectory || !staging.isDirectory) return failed("Instance or staging directory is missing")
        if (root.toPath().startsWith(staging.toPath()) || staging.toPath().startsWith(root.toPath())) {
            return failed("Instance and staging directories must be separate")
        }

        val clientEntries = inspection.overrideFiles.filter { it.startsWith("overrides/") }
        val serverCount = inspection.overrideFiles.count { it.startsWith("server-overrides/") }
        if (clientEntries.isEmpty()) return MinecraftModpackOverrideImportResult.Imported(emptyList(), serverCount)

        val manifestPaths = inspection.manifest.files.map { it.path.lowercase() }.toSet()
        val planned = mutableListOf<Pair<String, String>>()
        for (entry in clientEntries) {
            val relative = safeOverridePath(entry) ?: return failed("Archive contains an unsafe client override path")
            if (relative.lowercase() in manifestPaths) {
                return failed("Override conflicts with a manifest download destination: $relative")
            }
            val destination = File(root, relative)
            val canonicalParent = runCatching { destination.parentFile.canonicalFile }.getOrNull()
                ?: return failed("Invalid override destination")
            val canonicalDestination = File(canonicalParent, destination.name)
            if (!canonicalDestination.toPath().startsWith(root.toPath()) ||
                canonicalDestination == root || !safeLeaf(destination.name)
            ) return failed("Override destination escapes the instance directory")
            if (canonicalDestination.exists()) return failed("Override destination already exists: $relative")
            planned += relative to entry
        }
        if (planned.map { it.first.lowercase() }.distinct().size != planned.size) {
            return failed("Archive contains duplicate client override destinations")
        }

        val tempRoot = File(staging, "override-import-${System.nanoTime()}")
        if (!tempRoot.mkdir()) return failed("Could not create private override staging directory")
        val staged = mutableListOf<Pair<File, File>>()
        try {
            ZipFile(archiveFile).use { zip ->
                for ((relative, entryName) in planned) {
                    val entry = zip.getEntry(entryName) ?: throw IllegalStateException("Override entry disappeared")
                    if (entry.isDirectory || entry.size < 0L || entry.size > MAX_OVERRIDE_FILE_BYTES) {
                        throw IllegalStateException("Override entry is not a supported regular file")
                    }
                    val stagedFile = File(tempRoot, relative)
                    val stagedParent = stagedFile.parentFile
                    if (!stagedParent.mkdirs() && !stagedParent.isDirectory) {
                        throw IllegalStateException("Could not create temporary override directory")
                    }
                    val canonicalStaged = stagedFile.canonicalFile
                    if (!canonicalStaged.toPath().startsWith(tempRoot.toPath())) {
                        throw IllegalStateException("Override staging path escaped")
                    }
                    zip.getInputStream(entry).use { input ->
                        stagedFile.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > MAX_OVERRIDE_FILE_BYTES) {
                                    throw IllegalStateException("Override file exceeds size limit")
                                }
                                output.write(buffer, 0, count)
                            }
                            output.flush()
                        }
                    }
                    if (entry.size != stagedFile.length()) {
                        throw IllegalStateException("Override file size changed during extraction")
                    }
                    val destination = File(root, relative)
                    val parent = destination.parentFile.canonicalFile
                    val finalDestination = File(parent, destination.name)
                    if (!finalDestination.toPath().startsWith(root.toPath()) || finalDestination.exists()) {
                        throw IllegalStateException("Override destination changed during import")
                    }
                    staged += stagedFile to finalDestination
                }
            }

            val created = mutableListOf<File>()
            try {
                for ((source, destination) in staged) {
                    val parent = destination.parentFile
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw IllegalStateException("Could not create instance override directory")
                    }
                    if (destination.exists()) throw IllegalStateException("Override destination appeared during import")
                    Files.move(source.toPath(), destination.toPath())
                    created += destination
                }
            } catch (error: Exception) {
                val rollbackFailures = created.asReversed().mapNotNull { file ->
                    runCatching { Files.deleteIfExists(file.toPath()) }
                        .fold({ if (it) null else file.path }, { file.path })
                }
                return MinecraftModpackOverrideImportResult.Failed(
                    error.message ?: "Could not commit client overrides",
                    rollbackFailures
                )
            }
            return MinecraftModpackOverrideImportResult.Imported(
                installedPaths = staged.map { it.second.path },
                skippedServerFiles = serverCount
            )
        } catch (error: Exception) {
            return failed(error.message ?: "Could not extract client overrides")
        } finally {
            runCatching { tempRoot.deleteRecursively() }
        }
    }

    private fun safeOverridePath(entry: String): String? {
        if (!entry.startsWith("overrides/") || entry.startsWith("overrides//")) return null
        val relative = entry.removePrefix("overrides/")
        if (relative.isBlank() || relative.length > 1024 || relative.indexOf('\\') >= 0 ||
            relative.indexOf('\u0000') >= 0 || relative.startsWith('/')
        ) return null
        val segments = relative.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return null
        return segments.joinToString("/")
    }

    private fun safeLeaf(value: String): Boolean =
        value.isNotBlank() && value != "." && value != ".." &&
            value.none { it == '/' || it == '\\' || it == ':' || it.code < 32 || it.code == 127 }

    private fun failed(reason: String) = MinecraftModpackOverrideImportResult.Failed(reason)

    private companion object {
        const val MAX_OVERRIDE_FILE_BYTES = 128L * 1024L * 1024L
    }
}
