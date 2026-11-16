package com.mflegend.flamelauncher.runtime

/** Pure Java release metadata helpers, independent of Android APIs. */
internal object JavaRuntimeMetadata {
    private val releaseKey = Regex("[A-Z][A-Z0-9_]*")

    fun parseReleaseFile(content: String): Map<String, String> {
        val values = mutableMapOf<String, String>()
        for (line in content.lineSequence()) {
            val separator = line.indexOf('=')
            if (separator <= 0) continue
            val key = line.substring(0, separator).trim()
            val rawValue = line.substring(separator + 1).trim()
            if (!releaseKey.matches(key)) continue
            val value = rawValue.removeSurrounding("\"")
            if ('\u0000' !in value) values[key] = value
        }
        return values
    }

    fun parseJavaMajor(version: String): Int? {
        val normalized = version.removePrefix("1.")
        return normalized.substringBefore('.').substringBefore('-').toIntOrNull()
    }

    fun normalizeArchitecture(architecture: String): String = when (architecture.trim().lowercase()) {
        "aarch64", "arm64", "arm64-v8a" -> "arm64"
        "x86_64", "amd64" -> "x86_64"
        "x86", "i386", "i686" -> "x86"
        "arm", "arm32", "armv7l" -> "arm"
        else -> architecture.trim().lowercase()
    }

    fun matchesInitialArm64Process(
        architecture: String,
        is64BitProcess: Boolean,
        supportedAbis: List<String>
    ): Boolean = is64BitProcess &&
        supportedAbis.contains("arm64-v8a") &&
        architecture == "arm64"
}
