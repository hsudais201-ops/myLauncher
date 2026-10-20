package com.voidcraft.launcher.core

/**
 * Expands modern Minecraft launch-argument placeholders without splitting or
 * re-tokenizing argument strings. A placeholder value containing spaces remains
 * one argument, which is required for paths and some JVM property values.
 *
 * This is only the interpolation stage: callers must still provide a complete,
 * validated argument list and must not log the resulting list because it may
 * contain account tokens.
 */
object MinecraftLaunchArgumentInterpolator {
    private val placeholderPattern = Regex("\\$\\{([A-Za-z][A-Za-z0-9_.-]{0,127})}")
    private val candidatePattern = Regex("\\$\\{([^}]*)}")
    private val placeholderNamePattern = Regex("[A-Za-z][A-Za-z0-9_.-]{0,127}")
    private const val MAX_ARGUMENTS = 100_000
    private const val MAX_ARGUMENT_LENGTH = 65_536

    fun expand(arguments: List<String>, placeholders: Map<String, String>): List<String> {
        require(arguments.size <= MAX_ARGUMENTS) { "Launch argument count exceeds the supported limit" }
        require(placeholders.keys.all { placeholderNamePattern.matches(it) }) {
            "Launch placeholder map contains an invalid name"
        }
        require(placeholders.values.none { it.contains('\u0000') }) {
            "Launch placeholder values must not contain NUL characters"
        }

        return arguments.map { argument ->
            require(argument.length <= MAX_ARGUMENT_LENGTH) { "Launch argument exceeds the supported length" }
            require(!argument.contains('\u0000')) { "Launch arguments must not contain NUL characters" }

            candidatePattern.findAll(argument).forEach { candidate ->
                val name = candidate.groupValues[1]
                if (!placeholderNamePattern.matches(name)) {
                    throw InvalidLaunchPlaceholderException("Launch argument contains an invalid placeholder name")
                }
            }

            val expanded = placeholderPattern.replace(argument) { match ->
                val name = match.groupValues[1]
                placeholders[name] ?: throw UnresolvedLaunchPlaceholderException(name)
            }
            require(expanded.length <= MAX_ARGUMENT_LENGTH) {
                "Expanded launch argument exceeds the supported length"
            }
            expanded
        }
    }
}

class UnresolvedLaunchPlaceholderException(val placeholder: String) :
    IllegalArgumentException("No value was provided for launch placeholder: " + placeholder)

class InvalidLaunchPlaceholderException(message: String) : IllegalArgumentException(message)

/**
 * Combines platform-rule filtering and placeholder expansion for modern
 * metadata arguments. Legacy space-delimited minecraftArguments need a separate
 * compatibility parser and must not be passed through this class as one string.
 */
class MinecraftLaunchArgumentResolver(
    private val evaluator: MinecraftLaunchArgumentEvaluator = MinecraftLaunchArgumentEvaluator()
) {
    fun resolve(
        values: List<MinecraftArgumentValue>?,
        platform: MinecraftPlatformContext,
        placeholders: Map<String, String>
    ): List<String> = MinecraftLaunchArgumentInterpolator.expand(
        evaluator.flatten(values, platform),
        placeholders
    )
}
