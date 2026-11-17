package com.mflegend.flamelauncher.catalog

import org.json.JSONArray
import org.json.JSONObject

enum class MinecraftLaunchPlatform(val metadataName: String?) {
    WINDOWS("windows"),
    MAC_OS("osx"),
    LINUX("linux"),
    ANDROID(null),
    UNKNOWN(null)
}

/**
 * Facts used to evaluate Mojang's conditional launch arguments.
 *
 * Android is a distinct platform here; it must not inherit desktop Linux rules.
 */
data class MinecraftLaunchRuleContext(
    val platform: MinecraftLaunchPlatform,
    val osVersion: String = "",
    val osArchitecture: String = "",
    val features: Map<String, Boolean> = emptyMap()
)

/**
 * Resolves modern Mojang game/JVM argument arrays without invoking a shell.
 *
 * The caller supplies already-vetted placeholder values. Each value remains a
 * separate process argument, including values containing spaces. This class
 * does not build a classpath, authenticate an account, or start a JVM.
 */
object MinecraftArgumentResolver {
    fun resolveGameArguments(
        metadataBytes: ByteArray,
        expectedVersionId: String,
        context: MinecraftLaunchRuleContext,
        substitutions: Map<String, String>
    ): List<String> = resolveArguments(
        metadataBytes,
        expectedVersionId,
        "game",
        context,
        substitutions
    )

    fun resolveJvmArguments(
        metadataBytes: ByteArray,
        expectedVersionId: String,
        context: MinecraftLaunchRuleContext,
        substitutions: Map<String, String>
    ): List<String> = resolveArguments(
        metadataBytes,
        expectedVersionId,
        "jvm",
        context,
        substitutions
    )

    fun resolveLegacyGameArguments(
        metadataBytes: ByteArray,
        expectedVersionId: String,
        substitutions: Map<String, String>
    ): List<String> {
        MinecraftLaunchMetadataParser.parse(metadataBytes, expectedVersionId)
        val root = JSONObject(metadataBytes.toString(Charsets.UTF_8))
        require(root.optJSONObject("arguments") == null) {
            "Legacy argument resolution cannot be used with modern argument metadata."
        }
        val commandLine = root.optString("minecraftArguments").takeIf { it.isNotBlank() }
            ?: error("Version metadata does not contain legacy game arguments.")
        return tokenizeLegacyArguments(commandLine).map { expandPlaceholders(it, substitutions) }
    }

    private fun resolveArguments(
        metadataBytes: ByteArray,
        expectedVersionId: String,
        key: String,
        context: MinecraftLaunchRuleContext,
        substitutions: Map<String, String>
    ): List<String> {
        MinecraftLaunchMetadataParser.parse(metadataBytes, expectedVersionId)
        val root = JSONObject(metadataBytes.toString(Charsets.UTF_8))
        val argumentArray = root.optJSONObject("arguments")?.optJSONArray(key)
            ?: error("Version metadata does not contain modern '$key' arguments.")

        return buildList {
            for (index in 0 until argumentArray.length()) {
                when (val entry = argumentArray.get(index)) {
                    is String -> add(expandPlaceholders(entry, substitutions))
                    is JSONObject -> {
                        val rules = entry.optJSONArray("rules")
                            ?: error("Conditional '$key' argument is missing its rules array.")
                        if (rulesAllow(rules, context)) {
                            val value = entry.opt("value")
                            when (value) {
                                is String -> add(expandPlaceholders(value, substitutions))
                                is JSONArray -> {
                                    for (valueIndex in 0 until value.length()) {
                                        val token = value.get(valueIndex)
                                        require(token is String) {
                                            "Conditional '$key' argument contains a non-string value."
                                        }
                                        add(expandPlaceholders(token, substitutions))
                                    }
                                }
                                else -> error("Conditional '$key' argument has an invalid value.")
                            }
                        }
                    }
                    else -> error("Version metadata contains an unsupported '$key' argument entry.")
                }
            }
        }
    }

    private fun rulesAllow(rules: JSONArray, context: MinecraftLaunchRuleContext): Boolean {
        // An empty rule list means the argument is unconditional.
        var allowed = rules.length() == 0
        for (index in 0 until rules.length()) {
            val rule = rules.get(index) as? JSONObject
                ?: error("Version metadata contains a malformed launch rule.")
            val action = rule.optString("action")
            require(action == "allow" || action == "disallow") {
                "Version metadata contains an unknown launch rule action."
            }
            if (ruleMatches(rule, context)) {
                // Mojang rules are ordered; the last matching rule wins.
                allowed = action == "allow"
            }
        }
        return allowed
    }

    private fun ruleMatches(rule: JSONObject, context: MinecraftLaunchRuleContext): Boolean {
        if (rule.has("os")) {
            val os = rule.optJSONObject("os")
                ?: error("Version metadata contains an invalid operating-system rule.")
            if (os.has("name")) {
                val expectedName = os.optString("name")
                if (expectedName != context.platform.metadataName) return false
            }
            if (os.has("arch")) {
                val pattern = os.optString("arch")
                require(pattern.isNotEmpty()) { "Operating-system architecture pattern is empty." }
                if (!Regex(pattern).containsMatchIn(context.osArchitecture)) return false
            }
            if (os.has("version")) {
                val pattern = os.optString("version")
                require(pattern.isNotEmpty()) { "Operating-system version pattern is empty." }
                if (!Regex(pattern).containsMatchIn(context.osVersion)) return false
            }
        }

        if (rule.has("features")) {
            val features = rule.optJSONObject("features")
                ?: error("Version metadata contains invalid launch features.")
            val names = features.keys()
            while (names.hasNext()) {
                val name = names.next()
                val expected = features.get(name)
                require(expected is Boolean) { "Launch feature '$name' must be a boolean." }
                if ((context.features[name] ?: false) != expected) return false
            }
        }
        return true
    }

    private fun expandPlaceholders(value: String, substitutions: Map<String, String>): String =
        PLACEHOLDER.replace(value) { match ->
            val key = match.groupValues[1]
            substitutions[key] ?: error("No value was supplied for Minecraft launch placeholder '${'    private val PLACEHOLDER = Regex("""\$\{([A-Za-z0-9_.-]+)}""")
}
}{$key}'.")
        }

    private fun tokenizeLegacyArguments(commandLine: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var tokenStarted = false
        var index = 0

        while (index < commandLine.length) {
            val character = commandLine[index]
            if (character == '\\' && index + 1 < commandLine.length) {
                val next = commandLine[index + 1]
                if (next == '\\' || next == '"' || next == '\'') {
                    current.append(next)
                    tokenStarted = true
                    index += 2
                    continue
                }
            }

            if (quote != null) {
                if (character == quote) {
                    quote = null
                } else {
                    current.append(character)
                }
                tokenStarted = true
                index++
                continue
            }

            when {
                character == '"' || character == '\'' -> {
                    quote = character
                    tokenStarted = true
                }
                character.isWhitespace() -> {
                    if (tokenStarted) {
                        tokens += current.toString()
                        current.setLength(0)
                        tokenStarted = false
                    }
                }
                else -> {
                    current.append(character)
                    tokenStarted = true
                }
            }
            index++
        }

        require(quote == null) { "Legacy Minecraft arguments contain an unmatched quote." }
        if (tokenStarted) tokens += current.toString()
        return tokens
    }

    private val PLACEHOLDER = Regex("""\$\{([A-Za-z0-9_.-]+)}""")
}
