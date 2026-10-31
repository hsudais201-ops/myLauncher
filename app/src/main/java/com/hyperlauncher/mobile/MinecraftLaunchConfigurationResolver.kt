package com.hyperlauncher.mobile

import java.io.File

data class MinecraftLaunchConfiguration(
    val mainClass: String,
    val jvmArguments: List<String>,
    val gameArguments: List<String>,
    val classpath: String,
    val nativeDirectory: File,
    /** Mojang placeholders still requiring a real account/session value. */
    val unresolvedPlaceholders: Set<String>
) {
    val hasResolvedArguments: Boolean get() = unresolvedPlaceholders.isEmpty()
    /** Ordered arguments for ProcessBuilder: JVM options, main class, then game arguments. */
    fun processArguments(): List<String> = jvmArguments + mainClass + gameArguments
}

/**
 * Combines verified installed artifacts and official Mojang launch metadata.
 * Authentication/session placeholders must be supplied by the caller; this
 * resolver never fabricates a licensed account or access token.
 */
object MinecraftLaunchConfigurationResolver {
    fun resolve(
        details: MinecraftVersionDetails,
        plan: MinecraftInstallPlan,
        storageRoot: File,
        osName: String = "linux",
        osArch: String = System.getProperty("os.arch").orEmpty(),
        osVersion: String = System.getProperty("os.version").orEmpty(),
        placeholders: Map<String, String> = emptyMap(),
        features: Map<String, Boolean> = emptyMap()
    ): MinecraftLaunchConfiguration {
        require(details.id == plan.versionId) { "Version details and install plan do not match" }
        val classpath = MinecraftClasspathResolver.resolve(
            storageRoot,
            plan.clientJar.relativePath,
            plan.libraries.map { it.relativePath }
        )
        val expectedNativeDirectory = File(storageRoot, "versions/" + plan.versionId + "/" + plan.versionId + "-natives").canonicalFile
        val root = storageRoot.canonicalFile
        require(expectedNativeDirectory.path.startsWith(root.path + File.separator)) {
            "Minecraft native directory escapes the selected instance"
        }
        val generated = mapOf(
            "classpath" to classpath,
            "game_directory" to root.absolutePath,
            "assets_root" to File(root, "assets").canonicalPath,
            "assets_index_name" to plan.assetIndexFile.relativePath
                .removePrefix("assets/indexes/").removeSuffix(".json"),
            "natives_directory" to expectedNativeDirectory.absolutePath,
            "version_name" to plan.versionId
        )
        val resolvedPlaceholders = placeholders + generated
        val jvm = MinecraftLaunchArgumentResolver.resolve(
            details.launchArgumentsJson, "jvm", osName, osArch, osVersion,
            placeholders = resolvedPlaceholders, features = features
        ).toMutableList()
        if (jvm.none { it == "-cp" || it == "-classpath" }) {
            jvm += listOf("-cp", classpath)
        }
        val game = MinecraftLaunchArgumentResolver.resolve(
            details.launchArgumentsJson, "game", osName, osArch, osVersion,
            placeholders = resolvedPlaceholders, features = features
        )
        val unresolved = (jvm + game).flatMap { argument ->
            Regex("\\\\$\\\\{([A-Za-z0-9_.-]{1,80})}").findAll(argument)
                .map { it.groupValues[1] }
                .toList()
        }.toSet()
        return MinecraftLaunchConfiguration(
            details.mainClass, jvm, game, classpath, expectedNativeDirectory, unresolved
        )
    }
}
