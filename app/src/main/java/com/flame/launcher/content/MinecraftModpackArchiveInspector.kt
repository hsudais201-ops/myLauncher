package com.flame.launcher.content

import java.io.File
import java.util.zip.ZipFile

/**
 * Performs metadata-only validation of a Modrinth .mrpack archive.
 *
 * This deliberately does not extract files or download referenced content. Import must
 * consume only an archive that passes this inspection and must still validate each
 * extracted destination before writing it.
 */
class MinecraftModpackArchiveInspector(
    private val limits: Limits = Limits()
) {
    data class Limits(
        val maxEntries: Int = 10_000,
        val maxEntryBytes: Long = 512L * 1024L * 1024L,
        val maxTotalUncompressedBytes: Long = 2L * 1024L * 1024L * 1024L,
        val maxCompressionRatio: Long = 200L
    ) {
        init {
            require(maxEntries > 0)
            require(maxEntryBytes > 0)
            require(maxTotalUncompressedBytes > 0)
            require(maxCompressionRatio > 0)
        }
    }

    data class Inspection(
        val archive: File,
        val entryCount: Int,
        val totalUncompressedBytes: Long,
        val overrideFiles: List<String>
    )

    sealed interface Result {
        data class Accepted(val inspection: Inspection) : Result
        data class Rejected(val reason: String) : Result
    }

    fun inspect(archive: File): Result {
        if (!archive.isFile || !archive.canRead()) {
            return Result.Rejected("Modpack archive is missing or unreadable")
        }
        if (!archive.name.endsWith(".mrpack", ignoreCase = true)) {
            return Result.Rejected("Only Modrinth .mrpack archives are supported")
        }

        return try {
            ZipFile(archive).use { zip ->
                val seen = HashSet<String>()
                val overrides = mutableListOf<String>()
                var count = 0
                var totalBytes = 0L
                var hasRootManifest = false
                val entries = zip.entries()

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    count += 1
                    if (count > limits.maxEntries) {
                        return Result.Rejected("Archive contains too many entries")
                    }

                    val normalized = normalizeEntryName(entry.name)
                        ?: return Result.Rejected("Archive contains an unsafe path")
                    if (!seen.add(normalized)) {
                        return Result.Rejected("Archive contains duplicate paths")
                    }

                    if (!entry.isDirectory) {
                        val size = entry.size
                        val compressedSize = entry.compressedSize
                        if (size < 0L || compressedSize < 0L) {
                            return Result.Rejected("Archive contains an entry with unknown size")
                        }
                        if (size > limits.maxEntryBytes) {
                            return Result.Rejected("Archive contains an oversized file")
                        }
                        if (size > 0L) {
                            if (compressedSize == 0L ||
                                size.toDouble() / compressedSize.toDouble() > limits.maxCompressionRatio.toDouble()
                            ) {
                                return Result.Rejected("Archive contains a suspicious compression ratio")
                            }
                        }
                        totalBytes += size
                        if (totalBytes < 0L || totalBytes > limits.maxTotalUncompressedBytes) {
                            return Result.Rejected("Archive expands beyond the allowed size")
                        }

                        if (normalized == "modrinth.index.json") {
                            hasRootManifest = true
                        }
                        if (normalized.startsWith("overrides/") ||
                            normalized.startsWith("server-overrides/")
                        ) {
                            overrides += normalized
                        }
                    }
                }

                if (!hasRootManifest) {
                    Result.Rejected("Archive is missing its root modrinth.index.json manifest")
                } else {
                    Result.Accepted(
                        Inspection(
                            archive = archive.canonicalFile,
                            entryCount = count,
                            totalUncompressedBytes = totalBytes,
                            overrideFiles = overrides.sorted()
                        )
                    )
                }
            }
        } catch (error: Exception) {
            Result.Rejected("Could not inspect modpack archive: ${error.message ?: "invalid ZIP"}")
        }
    }

    private fun normalizeEntryName(raw: String): String? {
        if (raw.isBlank() || raw.indexOf('\u0000') >= 0 || raw.indexOf('\\') >= 0) return null
        if (raw.startsWith('/') || raw.startsWith("//")) return null
        if (raw.length >= 2 && raw[1] == ':') return null

        val withoutTrailingSlash = raw.removeSuffix("/")
        if (withoutTrailingSlash.isBlank()) return null
        val segments = withoutTrailingSlash.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return null
        return segments.joinToString("/")
    }
}
