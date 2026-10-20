package com.voidcraft.launcher.core

/**
 * One ordered argument entry from modern version metadata. Objects may include
 * OS/feature rules and a value that expands to one or more argument strings.
 * Placeholders are retained literally until a separately tested launch-context
 * substitution stage is implemented.
 */
data class MinecraftArgumentValue(
    val values: List<String>,
    val rules: List<MinecraftLibraryRule> = emptyList()
)

/**
 * Nullable game/JVM lists represent whether a field was present in the metadata.
 * This lets inheritance distinguish an absent field (inherit parent) from an
 * explicit empty array (override parent with no arguments for that group).
 */
data class MinecraftVersionArguments(
    val game: List<MinecraftArgumentValue>?,
    val jvm: List<MinecraftArgumentValue>?
)

/** Filters modern argument entries while preserving declaration and value order. */
class MinecraftLaunchArgumentEvaluator(
    private val ruleEvaluator: MinecraftLibraryRuleEvaluator = MinecraftLibraryRuleEvaluator()
) {
    fun flatten(
        values: List<MinecraftArgumentValue>?,
        platform: MinecraftPlatformContext
    ): List<String> = values.orEmpty()
        .asSequence()
        .filter { ruleEvaluator.isAllowed(it.rules, platform) }
        .flatMap { it.values.asSequence() }
        .toList()
}
