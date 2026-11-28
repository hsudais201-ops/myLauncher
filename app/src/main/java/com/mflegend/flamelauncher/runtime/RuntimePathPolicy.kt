package com.mflegend.flamelauncher.runtime

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.PriorityQueue

/**
 * Canonical-path policy for app-private runtime bundles.
 *
 * This blocks ordinary symlink/path escapes during inspection. It is not a
 * defense against a concurrent filesystem mutation (TOCTOU); callers must
 * keep the managed directory private and must not treat this as a loader
 * security boundary.
 */
internal object RuntimePathPolicy {
    private val runtimeIdPattern = Regex("[A-Za-z0-9._-]{1,80}")
    private const val MAX_RUNTIME_DIRECTORIES_TO_SCAN = 32

    data class RuntimeDirectoryBatch(
        val directories: List<File>,
        val omittedCount: Int,
        val ignoredEntryCount: Int
    )

    fun isRuntimeId(value: String): Boolean = runtimeIdPattern.matches(value)

    /**
     * Select at most 32 real runtime directories in deterministic name order.
     *
     * A bounded max-heap avoids retaining every candidate when storage has
     * accumulated many entries. Files and symbolic links with runtime-like
     * names do not consume the directory scan quota.
     */
    fun selectRuntimeDirectories(children: Sequence<File>): RuntimeDirectoryBatch {
        val selected = PriorityQueue<File>(
            MAX_RUNTIME_DIRECTORIES_TO_SCAN,
            compareByDescending<File> { it.name }
        )
        var eligibleCount = 0
        var ignoredEntryCount = 0

        children.forEach { child ->
            if (!isRuntimeId(child.name)) return@forEach
            if (isSymbolicLink(child) || !child.isDirectory) {
                ignoredEntryCount += 1
                return@forEach
            }

            eligibleCount += 1
            if (selected.size < MAX_RUNTIME_DIRECTORIES_TO_SCAN) {
                selected.add(child)
            } else if (child.name < selected.peek().name) {
                selected.poll()
                selected.add(child)
            }
        }

        return RuntimeDirectoryBatch(
            directories = selected.sortedBy { it.name },
            omittedCount = (eligibleCount - selected.size).coerceAtLeast(0),
            ignoredEntryCount = ignoredEntryCount
        )
    }

    /** Reject links even when they resolve back inside the managed tree. */
    fun isSymbolicLink(candidate: File): Boolean = try {
        Files.isSymbolicLink(candidate.toPath())
    } catch (_: SecurityException) {
        true
    }

    fun isDirectChild(parent: File, candidate: File): Boolean = try {
        val canonicalParent = parent.canonicalFile
        candidate.canonicalFile.parentFile == canonicalParent
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /**
     * Accept only strict descendants whose path components contain no
     * symbolic links, including links that resolve elsewhere inside the tree.
     */
    fun isSafeChild(parent: File, candidate: File): Boolean = try {
        val parentPath = parent.toPath().toAbsolutePath().normalize()
        val candidatePath = candidate.toPath().toAbsolutePath().normalize()
        candidatePath != parentPath &&
            candidatePath.startsWith(parentPath) &&
            !containsSymbolicLink(parentPath, candidatePath) &&
            candidate.canonicalFile.toPath().startsWith(parent.canonicalFile.toPath())
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun containsSymbolicLink(parent: Path, candidate: Path): Boolean {
        var current = parent
        if (Files.isSymbolicLink(current)) return true
        for (part in parent.relativize(candidate)) {
            current = current.resolve(part)
            if (Files.isSymbolicLink(current)) return true
        }
        return false
    }
}
