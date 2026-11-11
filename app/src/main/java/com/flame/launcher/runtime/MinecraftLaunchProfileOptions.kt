package com.flame.launcher.runtime

import com.flame.launcher.content.MinecraftInstanceLaunchSettings

/**
 * Validated, shell-free translation of saved per-instance preferences into
 * the command-line portions owned by Flame. Authentication, classpath,
 * runtime paths and other launcher-managed values remain immutable.
 */
data class MinecraftLaunchProfileOptions(
    val memory: MinecraftLaunchMemory,
    val width: Int,
    val height: Int,
    val fullscreen: Boolean,
    val jvmArguments: List<String>,
    val gameArguments: List<String>
)

object MinecraftLaunchProfileOptionsBuilder {
    fun build(settings: MinecraftInstanceLaunchSettings): MinecraftLaunchProfileOptions {
        settings.validationError()?.let { throw IllegalArgumentException(it) }
        return MinecraftLaunchProfileOptions(
            memory = MinecraftLaunchMemory(settings.minimumMemoryMb, settings.maximumMemoryMb),
            width = settings.width,
            height = settings.height,
            fullscreen = settings.fullscreen,
            jvmArguments = parseJvmArguments(settings.jvmArguments),
            gameArguments = parseGameArguments(settings.gameArguments)
        )
    }

    internal fun parseJvmArguments(raw: String): List<String> {
        val tokens = tokenize(raw)
        require(tokens.size <= MAX_ARGUMENTS) { "Too many extra JVM arguments." }
        tokens.forEach { token ->
            require(token.startsWith("-") && token.length > 1) {
                "Every extra JVM argument must be one complete JVM option."
            }
            val option = token.substringBefore('=')
            val normalized = option.lowercase()
            require(option !in BLOCKED_JVM_OPTIONS) {
                "Extra JVM argument '$option' is managed by Flame or is not supported."
            }
            require(!normalized.startsWith("-xms") && !normalized.startsWith("-xmx")) {
                "Set heap limits with the profile memory controls, not extra JVM arguments."
            }
            require(!normalized.startsWith("-javaagent:") &&
                !normalized.startsWith("-agentlib:") &&
                !normalized.startsWith("-agentpath:") &&
                !normalized.startsWith("-xrun") &&
                !normalized.startsWith("-xbootclasspath")
            ) { "Agents and boot-classpath overrides are not allowed in profile arguments." }
            require(!normalized.startsWith("-xx:onerror=") &&
                !normalized.startsWith("-xx:onoutofmemoryerror=") &&
                normalized != "-xx:+ignoreunrecognizedvmoptions" &&
                !normalized.startsWith("-xx:ignoreunrecognizedvmoptions=")
            ) { "This JVM option can execute external commands or bypass VM option validation." }

            if (token.startsWith("-D")) {
                val property = token.removePrefix("-D").substringBefore('=')
                require(PROPERTY_PATTERN.matches(property)) {
                    "Extra JVM system property name is invalid."
                }
                require(property !in BLOCKED_SYSTEM_PROPERTIES) {
                    "System property '$property' is managed by Flame."
                }
            }
        }
        return tokens
    }

    internal fun parseGameArguments(raw: String): List<String> {
        val tokens = tokenize(raw)
        require(tokens.size <= MAX_ARGUMENTS) { "Too many extra game arguments." }
        tokens.forEach { token ->
            val option = token.substringBefore('=')
            require(option !in BLOCKED_GAME_OPTIONS) {
                "Game argument '$option' is managed by Flame and cannot be overridden."
            }
        }
        return tokens
    }

    /**
     * Small command-line tokenizer only. It supports whitespace, single/double
     * quotes and backslash-escaped spaces/quotes; it does not run a shell, expand
     * variables, execute substitutions, or interpret command separators.
     */
    private fun tokenize(raw: String): List<String> {
        require(raw.length <= MAX_ARGUMENT_TEXT_LENGTH && raw.none { it.isISOControl() }) {
            "Argument text is too long or contains unsupported control characters."
        }
        val result = ArrayList<String>()
        val token = StringBuilder()
        var quote: Char? = null
        var tokenStarted = false
        var index = 0

        fun finishToken() {
            if (!tokenStarted) return
            require(token.isNotEmpty()) { "Empty command-line arguments are not supported." }
            require(token.length <= MAX_TOKEN_LENGTH) { "A command-line argument is too long." }
            result += token.toString()
            require(result.size <= MAX_ARGUMENTS) { "Too many command-line arguments." }
            token.setLength(0)
            tokenStarted = false
        }

        while (index < raw.length) {
            val current = raw[index]
            when {
                current == '\\' -> {
                    tokenStarted = true
                    if (index + 1 >= raw.length) {
                        throw IllegalArgumentException("Argument text ends with an incomplete escape.")
                    }
                    val next = raw[index + 1]
                    if (next == '\\' || next == '"' || next == '\'' || next.isWhitespace()) {
                        token.append(next)
                        index++
                    } else {
                        token.append(current)
                    }
                }
                quote != null -> {
                    if (current == quote) quote = null else token.append(current)
                    tokenStarted = true
                }
                current == '"' || current == '\'' -> {
                    quote = current
                    tokenStarted = true
                }
                current.isWhitespace() -> finishToken()
                else -> {
                    token.append(current)
                    tokenStarted = true
                }
            }
            require(token.length <= MAX_TOKEN_LENGTH) { "A command-line argument is too long." }
            index++
        }
        require(quote == null) { "Argument text contains an unclosed quote." }
        finishToken()
        return result
    }

    private val BLOCKED_JVM_OPTIONS = setOf(
        "-cp", "-classpath", "--class-path", "-jar", "-m", "--module",
        "-p", "--module-path", "--upgrade-module-path", "--patch-module",
        "--limit-modules", "--add-modules", "-xbootclasspath",
        "-xbootclasspath/a", "-xbootclasspath/p"
    )
    private val BLOCKED_SYSTEM_PROPERTIES = setOf(
        "java.class.path",
        "java.home",
        "user.dir",
        "java.library.path",
        "org.lwjgl.librarypath",
        "minecraft.launcher.brand",
        "minecraft.launcher.version",
        "user.home",
        "java.io.tmpdir",
        "jna.boot.library.path",
        "sun.boot.library.path",
        "java.system.class.loader",
        "java.security.manager",
        "java.ext.dirs",
        "java.endorsed.dirs"
    )
    private val BLOCKED_GAME_OPTIONS = setOf(
        "--username", "--version", "--versionType", "--accessToken", "--access-token",
        "--uuid", "--userType", "--gameDir", "--assetsDir", "--assetIndex",
        "--clientId", "--xuid", "--userProperties", "--profileProperties",
        "--width", "--height", "--fullscreen", "--fullscreenWidth", "--fullscreenHeight",
        "--game_directory", "--assets_root", "--asset_index", "--version_type",
        "--auth_player_name", "--auth_uuid", "--auth_access_token", "--auth_session"
    )
    private val PROPERTY_PATTERN = Regex("[A-Za-z0-9_.-]{1,128}")
    private const val MAX_ARGUMENT_TEXT_LENGTH = 2048
    private const val MAX_TOKEN_LENGTH = 512
    private const val MAX_ARGUMENTS = 64
}
