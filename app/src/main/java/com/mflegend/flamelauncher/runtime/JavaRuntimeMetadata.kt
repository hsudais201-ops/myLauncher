package com.mflegend.flamelauncher.runtime

/** Pure Java release metadata helpers, independent of Android APIs. */
internal object JavaRuntimeMetadata {
    private val releaseKey = Regex("[A-Z][A-Z0-9_]*")
    private val javaVersionPattern = Regex("^(?:1\\.(8)(?:\\.[0-9]+)*(?:_[0-9]+)?|(?!1\\.)([0-9]+)(?:\\.[0-9]+)*(?:_[0-9]+)?)(?:-[A-Za-z0-9]+)?(?:\\+[0-9]+(?:-[A-Za-z0-9]+)?)?$")

    fun parseReleaseFile(content: String): Map<String, String> {
        val values = mutableMapOf<String, String>()
        val seenKeys = mutableSetOf<String>()
        val ambiguousKeys = mutableSetOf<String>()
        for (line in content.lineSequence()) {
            val separator = line.indexOf('=')
            if (separator <= 0) continue
            val key = line.substring(0, separator).trim()
            val rawValue = line.substring(separator + 1).trim()
            if (!releaseKey.matches(key) || key in ambiguousKeys) continue
            if (!seenKeys.add(key)) {
                values.remove(key)
                ambiguousKeys += key
                continue
            }
            val hasOpeningQuote = rawValue.startsWith('"')
            val hasClosingQuote = rawValue.endsWith('"')
            if (hasOpeningQuote != hasClosingQuote) continue
            val value = if (hasOpeningQuote) rawValue.substring(1, rawValue.length - 1) else rawValue
            if ('\u0000' !in value && '"' !in value) values[key] = value
        }
        return values
    }

    fun parseJavaMajor(version: String): Int? {
        val match = javaVersionPattern.matchEntire(version.trim()) ?: return null
        return (match.groupValues[1].ifEmpty { match.groupValues[2] }).toIntOrNull()
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
