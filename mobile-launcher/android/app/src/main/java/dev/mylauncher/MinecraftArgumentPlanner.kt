package dev.mylauncher

import org.json.JSONArray
import org.json.JSONObject

/** A metadata-derived argument list. This is planning only; it does not start a JVM. */
data class MinecraftArgumentPlan(
    val mainClass: String,
    val jvmArguments: List<String>,
    val gameArguments: List<String>,
    val javaMajorVersion: Int?
)

/**
 * Builds a version-specific argument plan from official version JSON.
 * Platform rule evaluation here intentionally models a Linux ARM64 target only;
 * it must not be treated as proof that Linux native libraries work on Android.
 */
object MinecraftArgumentPlanner {
    fun parse(versionJson: String, placeholders: Map<String, String>): MinecraftArgumentPlan {
        val root = JSONObject(versionJson)
        val mainClass = root.optString("mainClass")
        require(mainClass.isNotBlank()) { "Version metadata has no mainClass." }

        val javaMajor = root.optJSONObject("javaVersion")?.optInt("majorVersion")
            ?.takeIf { it > 0 }
        val args = root.optJSONObject("arguments")
        val jvm = mutableListOf<String>()
        val game = mutableListOf<String>()
        if (args != null) {
            appendModernArguments(args.optJSONArray("jvm"), jvm, placeholders)
            appendModernArguments(args.optJSONArray("game"), game, placeholders)
        } else {
            // Older metadata uses a single legacy string for game arguments.
            val legacy = root.optString("minecraftArguments")
            require(legacy.isNotBlank()) { "Version metadata contains no supported argument format." }
            game += tokenizeLegacy(legacy).map { substitute(it, placeholders) }
            // A conservative baseline; version-specific JVM flags are not guessed.
            jvm += listOf("-Djava.library.path=\${natives_directory}")
        }
        return MinecraftArgumentPlan(mainClass, jvm, game, javaMajor)
    }

    private fun appendModernArguments(array: JSONArray?, output: MutableList<String>, placeholders: Map<String, String>) {
        if (array == null) return
        for (i in 0 until array.length()) {
            when (val item = array.get(i)) {
                is String -> output += substitute(item, placeholders)
                is JSONObject -> {
                    if (!rulesAllow(item.optJSONArray("rules"))) continue
                    when (val value = item.opt("value")) {
                        is String -> output += substitute(value, placeholders)
                        is JSONArray -> for (j in 0 until value.length()) {
                            val part = value.opt(j)
                            if (part is String) output += substitute(part, placeholders)
                        }
                    }
                }
            }
        }
    }

    /** Mojang rules are ordered: the last matching action decides. */
    private fun rulesAllow(rules: JSONArray?): Boolean {
        if (rules == null) return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            val os = rule.optJSONObject("os")
            if (os != null) {
                if (os.has("name") && os.optString("name") != "linux") continue
                if (os.has("arch")) {
                    val pattern = os.optString("arch")
                    val matches = try {
                        Regex(pattern).matches("aarch64") || Regex(pattern).matches("arm64")
                    } catch (_: Exception) { false }
                    if (!matches) continue
                }
                if (os.has("version")) {
                    val matches = try {
                        Regex(os.optString("version")).matches(System.getProperty("os.version", ""))
                    } catch (_: Exception) { false }
                    if (!matches) continue
                }
            }
            val features = rule.optJSONObject("features")
            if (features != null && features.keys().asSequence().any { features.optBoolean(it) }) continue
            allowed = rule.optString("action") == "allow"
        }
        return allowed
    }

    private fun substitute(value: String, placeholders: Map<String, String>): String {
        var result = value
        for ((key, replacement) in placeholders) {
            result = result.replace("${'$'}{$key}", replacement)
        }
        return result
    }

    /** Handles simple quoted tokens and escaped quotes in legacy argument strings. */
    internal fun tokenizeLegacy(input: String): List<String> {
        val result = mutableListOf<String>()
        val token = StringBuilder()
        var quote: Char? = null
        var escaped = false
        var tokenStarted = false
        for (char in input) {
            if (escaped) {
                token.append(char)
                escaped = false
                tokenStarted = true
            } else if (char == '\\') {
                escaped = true
                tokenStarted = true
            } else if (quote != null) {
                if (char == quote) quote = null else token.append(char)
                tokenStarted = true
            } else if (char == '\'' || char == '"') {
                quote = char
                tokenStarted = true
            } else if (char.isWhitespace()) {
                if (tokenStarted) {
                    result += token.toString()
                    token.setLength(0)
                    tokenStarted = false
                }
            } else {
                token.append(char)
                tokenStarted = true
            }
        }
        if (escaped) token.append('\\')
        require(quote == null) { "Unclosed quote in legacy Minecraft arguments." }
        if (tokenStarted) result += token.toString()
        return result
    }
}
