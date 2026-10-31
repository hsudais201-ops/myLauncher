package com.hyperlauncher.mobile

import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads Mojang's structured game/JVM argument arrays without invoking a shell.
 * Each JSON string remains one process argument; callers must pass the result
 * directly to ProcessBuilder rather than joining it into a command string.
 */
object MinecraftLaunchArgumentResolver {
    fun resolve(
        json: String,
        argumentType: String,
        osName: String,
        osArch: String,
        osVersion: String,
        features: Map<String, Boolean> = emptyMap(),
        placeholders: Map<String, String> = emptyMap()
    ): List<String> {
        require(argumentType == "game" || argumentType == "jvm") { "Unsupported Minecraft argument type" }
        require(osName in setOf("windows", "osx", "linux")) { "Unsupported operating system" }
        val root = JSONObject(json)
        val arguments = root.optJSONObject("arguments")
            ?: return emptyLegacyArguments(root, argumentType)
        val entries = arguments.optJSONArray(argumentType) ?: JSONArray()
        val output = ArrayList<String>()
        for (index in 0 until entries.length()) {
            val entry = entries.get(index)
            when (entry) {
                is String -> output += substitute(entry, placeholders)
                is JSONObject -> {
                    if (!matchesRules(entry.optJSONArray("rules"), osName, osArch, osVersion, features)) continue
                    val value = entry.opt("value")
                    when (value) {
                        is String -> output += substitute(value, placeholders)
                        is JSONArray -> for (valueIndex in 0 until value.length()) {
                            val token = value.optString(valueIndex, "")
                            require(token.isNotEmpty()) { "Minecraft argument contains an empty token" }
                            output += substitute(token, placeholders)
                        }
                        else -> throw IllegalArgumentException("Minecraft argument has an invalid value")
                    }
                }
                else -> throw IllegalArgumentException("Minecraft argument entry has an invalid type")
            }
        }
        return output
    }

    private fun emptyLegacyArguments(root: JSONObject, type: String): List<String> {
        // Pre-1.13 manifests use minecraftArguments (game arguments only).
        if (type != "game") return emptyList()
        val legacy = root.optString("minecraftArguments")
        return if (legacy.isBlank()) emptyList() else legacy.trim().split(Regex("\\s+"))
    }

    internal fun matchesRules(
        rules: JSONArray?,
        osName: String,
        osArch: String,
        osVersion: String,
        features: Map<String, Boolean>
    ): Boolean {
        if (rules == null || rules.length() == 0) return true
        var allowed = (0 until rules.length()).none { rules.getJSONObject(it).optString("action") == "allow" }
        for (index in 0 until rules.length()) {
            val rule = rules.getJSONObject(index)
            val os = rule.optJSONObject("os")
            val osMatches = os == null || (
                (os.optString("name").isBlank() || os.optString("name") == osName) &&
                (os.optString("arch").isBlank() || regexMatches(os.optString("arch"), osArch)) &&
                (os.optString("version").isBlank() || regexMatches(os.optString("version"), osVersion))
            )
            val requiredFeatures = rule.optJSONObject("features")
            val featureMatches = requiredFeatures == null || requiredFeatures.keys().asSequence().all { key ->
                (features[key] ?: false) == requiredFeatures.optBoolean(key)
            }
            if (osMatches && featureMatches) {
                allowed = rule.optString("action") == "allow"
            }
        }
        return allowed
    }

    private fun regexMatches(pattern: String, value: String): Boolean =
        runCatching { Regex(pattern).containsMatchIn(value) }.getOrDefault(false)

    private fun substitute(value: String, placeholders: Map<String, String>): String {
        require(value.length <= 16_384 && !value.contains('\u0000')) { "Minecraft argument is invalid" }
        return Regex("\\$\\{([A-Za-z0-9_.-]{1,80})}").replace(value) { match ->
            placeholders[match.groupValues[1]] ?: match.value
        }
    }
}
