package com.hyperlauncher.mobile

import org.json.JSONArray
import org.json.JSONObject

data class MinecraftLibrary(val name: String, val artifact: PlannedMinecraftFile)

/** Resolves trusted Maven artifacts using the selected device's Mojang rule inputs. */
object MinecraftLibraryResolver {
    fun parse(
        versionId: String,
        json: String,
        osName: String = "linux",
        osArch: String = System.getProperty("os.arch").orEmpty(),
        osVersion: String = System.getProperty("os.version").orEmpty()
    ): List<MinecraftLibrary> {
        require(Regex("^[0-9][A-Za-z0-9._-]{0,63}$").matches(versionId)) {
            "Invalid Minecraft version ID"
        }
        val libraries = JSONObject(json).optJSONArray("libraries") ?: JSONArray()
        val result = ArrayList<MinecraftLibrary>()
        for (index in 0 until libraries.length()) {
            val library = libraries.getJSONObject(index)
            if (!isAllowedByRules(library.optJSONArray("rules"), osName, osArch, osVersion)) continue
            val name = library.optString("name")
            require(name.isNotBlank() && name.length <= 512) { "Library has an invalid Maven name" }
            val artifact = library.optJSONObject("downloads")?.optJSONObject("artifact") ?: continue
            val path = artifact.optString("path")
            require(isSafeMavenPath(path)) { "Library artifact path is unsafe" }
            val descriptor = MinecraftDownload(
                url = artifact.optString("url"),
                sha1 = artifact.optString("sha1").lowercase(),
                size = artifact.optLong("size", -1L)
            )
            require(MinecraftVersionDetailsResolver.isTrustedDownloadUrl(descriptor.url)) {
                "Library download URL is not trusted"
            }
            require(Regex("^[a-f0-9]{40}$").matches(descriptor.sha1)) {
                "Library artifact has an invalid SHA-1 checksum"
            }
            require(descriptor.size > 0L) { "Library artifact has an invalid size" }
            result += MinecraftLibrary(
                name,
                PlannedMinecraftFile(descriptor, "libraries/$path", "library")
            )
        }
        return result.distinctBy { it.artifact.relativePath }
    }

    internal fun isSafeClassifierPath(path: String): Boolean = isSafeMavenPath(path)

    private fun isSafeMavenPath(path: String): Boolean {
        if (path.isBlank() || path.length > 1024 || path.startsWith("/") ||
            path.startsWith("\\") || path.contains('\\')) return false
        val segments = path.split('/')
        return segments.all { it.isNotBlank() && it != "." && it != ".." } &&
            segments.last().contains('.')
    }

    internal fun isAllowedByRules(
        rules: JSONArray?,
        osName: String,
        osArch: String,
        osVersion: String
    ): Boolean {
        if (rules == null || rules.length() == 0) return true
        var allowed = (0 until rules.length()).none {
            rules.getJSONObject(it).optString("action") == "allow"
        }
        for (index in 0 until rules.length()) {
            val rule = rules.getJSONObject(index)
            val os = rule.optJSONObject("os")
            val osMatches = os == null || (
                (os.optString("name").isBlank() || os.optString("name") == osName) &&
                (os.optString("arch").isBlank() || runCatching {
                    Regex(os.optString("arch")).containsMatchIn(osArch)
                }.getOrDefault(false)) &&
                (os.optString("version").isBlank() || runCatching {
                    Regex(os.optString("version")).containsMatchIn(osVersion)
                }.getOrDefault(false))
            )
            // This resolver has no demo/custom-resolution/quick-play flags yet.
            // A rule requiring a true feature therefore cannot match.
            val features = rule.optJSONObject("features")
            val featuresMatch = features == null ||
                features.keys().asSequence().all { key -> !features.optBoolean(key) }
            if (osMatches && featuresMatch) {
                allowed = rule.optString("action") == "allow"
            }
        }
        return allowed
    }
}
