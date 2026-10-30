package com.hyperlauncher.mobile

import org.json.JSONObject

/**
 * Resolves only native classifiers explicitly declared by Mojang's version JSON.
 * Android currently uses the Linux classifier family when Mojang provides one;
 * absence of a matching classifier is not silently replaced with another OS.
 */
object MinecraftNativeLibraryResolver {
    fun parse(
        versionId: String,
        json: String,
        osName: String = "linux",
        osArch: String = System.getProperty("os.arch").orEmpty()
    ): List<PlannedMinecraftFile> {
        require(Regex("^[0-9][A-Za-z0-9._-]{0,63}$").matches(versionId)) {
            "Invalid Minecraft version ID"
        }
        val architecture = when (osArch.lowercase()) {
            "x86_64", "amd64" -> "64"
            "x86", "i386", "i686" -> "32"
            else -> return emptyList()
        }
        val libraries = JSONObject(json).optJSONArray("libraries") ?: return emptyList()
        val result = ArrayList<PlannedMinecraftFile>()
        for (index in 0 until libraries.length()) {
            val library = libraries.getJSONObject(index)
            if (!MinecraftLibraryResolver.isAllowedByRules(library.optJSONArray("rules"), osName, osArch, System.getProperty("os.version").orEmpty())) continue
            val nativeKey = library.optJSONObject("natives")?.optString(osName).orEmpty()
            if (nativeKey.isBlank()) continue
            val classifierName = nativeKey.replace("${'$'}{arch}", architecture)
            require(Regex("^[A-Za-z0-9_.-]{1,128}$").matches(classifierName)) {
                "Native classifier name is invalid"
            }
            val classifier = library.optJSONObject("downloads")
                ?.optJSONObject("classifiers")
                ?.optJSONObject(classifierName) ?: continue
            val path = classifier.optString("path")
            require(isSafePath(path)) {
                "Native classifier path is unsafe"
            }
            val descriptor = MinecraftDownload(
                classifier.optString("url"),
                classifier.optString("sha1").lowercase(),
                classifier.optLong("size", -1L)
            )
            require(MinecraftVersionDetailsResolver.isTrustedDownloadUrl(descriptor.url)) {
                "Native classifier URL is not trusted"
            }
            require(Regex("^[a-f0-9]{40}$").matches(descriptor.sha1) && descriptor.size > 0L) {
                "Native classifier checksum or size is invalid"
            }
            result += PlannedMinecraftFile(descriptor, "libraries/$path", "native-library")
        }
        return result.distinctBy { it.relativePath }
    }

    private fun isSafePath(path: String): Boolean {
        if (path.isBlank() || path.length > 1024 || path.startsWith("/") || path.startsWith("\\") || path.contains('\\')) return false
        val parts = path.split('/')
        return parts.all { it.isNotBlank() && it != "." && it != ".." } && parts.last().contains('.')
    }

}
