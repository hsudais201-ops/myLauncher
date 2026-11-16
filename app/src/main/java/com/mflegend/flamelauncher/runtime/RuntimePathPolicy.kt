package com.mflegend.flamelauncher.runtime

import java.io.File
import java.io.IOException
import java.nio.file.Files

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

    fun isRuntimeId(value: String): Boolean = runtimeIdPattern.matches(value)

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

    fun isSafeChild(parent: File, candidate: File): Boolean = try {
        val parentPath = parent.canonicalPath + File.separator
        candidate.canonicalPath.startsWith(parentPath)
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
