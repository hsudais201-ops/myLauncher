package com.mflegend.flamelauncher.catalog

/**
 * Validates Maven artifact destinations before they are joined to a SAF directory.
 * Returns path segments only; callers must create each segment as a document.
 */
internal object LibraryArtifactPathPolicy {
    fun segments(path: String): List<String> {
        require(
            path.isNotBlank() &&
                path.length <= 1024 &&
                !path.startsWith("/") &&
                '\\\\' !in path &&
                '\\u0000' !in path
        ) { "Library artifact contains an unsafe relative path." }
        val parts = path.split('/')
        require(parts.size in 1..32 && parts.all {
            it.isNotBlank() &&
                it != "." &&
                it != ".." &&
                it.matches(Regex("[A-Za-z0-9._+\\\\-]+"))
        }) { "Library artifact contains an unsafe relative path." }
        return parts
    }
}
