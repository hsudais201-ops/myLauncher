package com.mflegend.flamelauncher.catalog

import org.json.JSONArray
import org.json.JSONObject

/** Actual platform facts used for Mojang library metadata rules. */
internal data class MojangLibraryRuleContext(
    val platformName: String = "android",
    val osVersion: String,
    val osArchitecture: String,
    val features: Map<String, Boolean?> = emptyMap()
)

/**
 * Evaluates Mojang library include/exclude rules without treating Android as Linux.
 *
 * A library that declares rules starts denied. Rules are evaluated in order and
 * the last matching rule wins. Unknown account/device features are not guessed.
 */
internal object MojangLibraryRulePolicy {
    fun isAllowed(library: JSONObject, context: MojangLibraryRuleContext): Boolean {
        if (!library.has("rules")) return true
        val rules = library.optJSONArray("rules")
            ?: error("Minecraft library rules must be an array.")
        var allowed = false
        for (index in 0 until rules.length()) {
            val rule = rules.optJSONObject(index)
                ?: error("Minecraft library contains a malformed rule.")
            val action = rule.optString("action")
            require(action == "allow" || action == "disallow") {
                "Minecraft library contains an unknown rule action."
            }
            if (matches(rule, context)) {
                allowed = action == "allow"
            }
        }
        return allowed
    }

    private fun matches(rule: JSONObject, context: MojangLibraryRuleContext): Boolean {
        if (rule.has("os")) {
            val os = rule.optJSONObject("os")
                ?: error("Minecraft library contains an invalid operating-system rule.")
            if (os.has("name")) {
                val expectedName = os.optString("name")
                if (expectedName != context.platformName) return false
            }
            if (os.has("arch")) {
                val pattern = os.optString("arch")
                require(pattern.isNotEmpty()) { "Minecraft library architecture pattern is empty." }
                if (!Regex(pattern).containsMatchIn(context.osArchitecture)) return false
            }
            if (os.has("version")) {
                val pattern = os.optString("version")
                require(pattern.isNotEmpty()) { "Minecraft library OS version pattern is empty." }
                if (!Regex(pattern).containsMatchIn(context.osVersion)) return false
            }
        }

        if (rule.has("features")) {
            val features = rule.optJSONObject("features")
                ?: error("Minecraft library contains invalid feature rules.")
            val keys = features.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val expected = features.get(name)
                require(expected is Boolean) {
                    "Minecraft library feature '$name' must be a boolean."
                }
                val actual = context.features[name] ?: return false
                if (actual != expected) return false
            }
        }
        return true
    }
}
