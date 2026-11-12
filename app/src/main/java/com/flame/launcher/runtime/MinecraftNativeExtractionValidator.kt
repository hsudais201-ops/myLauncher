package com.flame.launcher.runtime

import java.io.File
import java.security.MessageDigest
import java.nio.file.Files

/**
 * Verifies the promoted native directory against the exact files reported by
 * the extractor. Rejects symlinks, unexpected files, missing files and unsafe
 * paths. This is a post-extraction integrity check, not a binary ABI check.
 */
class MinecraftNativeExtractionValidator {
    fun validate(
        nativeDirectory: File,
        extractedFiles: List<File>
    ): MinecraftNativeExtractionValidation {
        if (!nativeDirectory.exists() || !nativeDirectory.isDirectory ||
            Files.isSymbolicLink(nativeDirectory.toPath())
        ) return MinecraftNativeExtractionValidation.InvalidDirectory

        val root = runCatching { nativeDirectory.canonicalFile }.getOrNull()
            ?: return MinecraftNativeExtractionValidation.InvalidDirectory
        if (root != nativeDirectory.absoluteFile) {
            return MinecraftNativeExtractionValidation.InvalidDirectory
        }

        val expected = LinkedHashSet<String>()
        for (file in extractedFiles) {
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Minecraft native extraction validation was cancelled.")
            }
            if (Files.isSymbolicLink(file.toPath()) || file.canonicalFile != file.absoluteFile) {
                return MinecraftNativeExtractionValidation.InvalidPlan
            }
            val relative = runCatching { file.relativeTo(root).invariantSeparatorsPath }.getOrNull()
                ?: return MinecraftNativeExtractionValidation.InvalidPlan
            if (!isSafeRelativePath(relative) || !expected.add(relative)) {
                return MinecraftNativeExtractionValidation.InvalidPlan
            }
        }

        val actual = LinkedHashSet<String>()
        val missing = ArrayList<String>()
        val corrupt = ArrayList<String>()
        val unexpected = ArrayList<String>()
        val discovered = collectFiles(root) ?: return MinecraftNativeExtractionValidation.InvalidDirectory
        discovered.forEach { file ->
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Minecraft native extraction validation was cancelled.")
            }
            val relative = file.relativeTo(root).invariantSeparatorsPath
            if (!actual.add(relative)) return MinecraftNativeExtractionValidation.InvalidDirectory
            if (relative !in expected) unexpected += relative
        }
        expected.forEach { relative ->
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Minecraft native extraction validation was cancelled.")
            }
            val file = File(root, relative)
            if (!file.exists() || !file.isFile || Files.isSymbolicLink(file.toPath())) {
                missing += relative
            } else if (runCatching { file.canonicalFile == file.absoluteFile }.getOrDefault(false).not()) {
                corrupt += relative
            }
        }

        return if (missing.isEmpty() && corrupt.isEmpty() && unexpected.isEmpty() &&
            actual == expected
        ) {
            MinecraftNativeExtractionValidation.Complete(
                verifiedFileCount = expected.size,
                verifiedBytes = discovered.sumOf { it.length() },
                sha1ByPath = discovered.associate {
                    it.relativeTo(root).invariantSeparatorsPath to sha1(it)
                }
            )
        } else {
            MinecraftNativeExtractionValidation.Incomplete(missing, corrupt, unexpected)
        }
    }

    private fun collectFiles(root: File): List<File>? {
        val result = ArrayList<File>()
        fun visit(directory: File): Boolean {
            val children = directory.listFiles() ?: return false
            for (child in children) {
                if (Files.isSymbolicLink(child.toPath()) || child.canonicalFile != child.absoluteFile) {
                    return false
                }
                if (child.isDirectory) {
                    if (!visit(child)) return false
                } else if (child.isFile) {
                    result += child
                } else {
                    return false
                }
            }
            return true
        }
        return if (visit(root)) result else null
    }

    private fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() && !path.startsWith('/') && '\\\\' !in path && ':' !in path &&
            path.split('/').all { it.isNotBlank() && it != "." && it != ".." }

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException("Minecraft native extraction validation was cancelled.")
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

sealed interface MinecraftNativeExtractionValidation {
    data class Complete(
        val verifiedFileCount: Int,
        val verifiedBytes: Long,
        val sha1ByPath: Map<String, String>
    ) : MinecraftNativeExtractionValidation

    data class Incomplete(
        val missingFiles: List<String>,
        val corruptFiles: List<String>,
        val unexpectedFiles: List<String>
    ) : MinecraftNativeExtractionValidation

    data object InvalidDirectory : MinecraftNativeExtractionValidation
    data object InvalidPlan : MinecraftNativeExtractionValidation
}
