package com.mflegend.flamelauncher.catalog

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
}
