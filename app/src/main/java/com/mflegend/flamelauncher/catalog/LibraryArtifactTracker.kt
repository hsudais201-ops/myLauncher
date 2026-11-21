package com.mflegend.flamelauncher.catalog

import java.util.Locale

/**
 * Validates library artifact declarations before storage access or download.
 *
 * SAF providers may be case-insensitive, so destinations are compared using
 * Locale.ROOT. File/directory prefix conflicts are rejected in either order.
 * The aggregate limit counts unique declared artifacts, including cached files.
 */
internal class LibraryArtifactTracker(
    private val maxArtifactBytes: Long,
    private val maxTotalBytes: Long
) {
    private data class Declaration(val size: Long, val sha1: String)

    private val declarations = HashMap<String, Declaration>()

    var totalDeclaredBytes: Long = 0
        private set

    /** Returns true for a new destination and false for an identical duplicate. */
    fun register(path: String, size: Long, sha1: String): Boolean {
        require(path.isNotBlank()) { "Library artifact path is missing." }
        require(size in 1..maxArtifactBytes) {
            "Library artifact size is missing or exceeds the safety limit."
        }
        require(sha1.matches(Regex("[a-f0-9]{40}"))) {
            "Library artifact SHA-1 is invalid."
        }

        val normalizedPath = path.lowercase(Locale.ROOT)
        val declaration = Declaration(size, sha1)
        val previous = declarations[normalizedPath]
        if (previous != null) {
            require(previous == declaration) {
                "Library metadata declares conflicting artifacts for the same path: $path"
            }
            return false
        }

        require(declarations.keys.none { existing ->
            normalizedPath.startsWith("$existing/") || existing.startsWith("$normalizedPath/")
        }) {
            "Library metadata declares colliding file and directory destinations: $path"
        }
        require(totalDeclaredBytes <= maxTotalBytes - size) {
            "Library set exceeds the total download safety limit."
        }
        declarations[normalizedPath] = declaration
        totalDeclaredBytes += size
        return true
    }
}
