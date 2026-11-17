package com.mflegend.flamelauncher.catalog

import java.util.Locale

/** Validates logical asset names before materializing them into game directories. */
internal object AssetLogicalPathPolicy {
    fun segments(path: String): List<String> {
        require(
            path.isNotBlank() &&
                path.length <= 1024 &&
                !path.startsWith("/") &&
                '\\' !in path &&
                path.indexOf(0.toChar()) < 0
        ) { "Asset index contains an unsafe logical path." }
        val parts = path.split('/')
        require(parts.size in 1..32 && parts.all {
            it.isNotBlank() &&
                it != "." &&
                it != ".." &&
                it.length <= 255 &&
                it.matches(Regex("[A-Za-z0-9._+\\-]+"))
        }) { "Asset index contains an unsafe logical path." }
        return parts
    }

    /**
     * Rejects duplicate names and file/directory prefix conflicts using
     * case-insensitive comparisons. SAF providers may be case-insensitive,
     * so accepting both forms could overwrite or misplace an asset.
     */
    fun validateUnique(paths: Collection<List<String>>) {
        val normalized = paths.map { it.joinToString("/") { part -> part.lowercase(Locale.ROOT) } }.sorted()
        for (index in normalized.indices) {
            val current = normalized[index]
            if (index > 0) {
                val previous = normalized[index - 1]
                require(previous != current) {
                    "Asset index contains duplicate logical paths that differ only by case."
                }
                require(!current.startsWith("$previous/")) {
                    "Asset index contains a file/directory path conflict."
                }
            }
        }
    }
}
