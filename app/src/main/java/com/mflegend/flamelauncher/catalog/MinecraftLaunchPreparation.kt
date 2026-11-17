package com.mflegend.flamelauncher.catalog

import com.mflegend.flamelauncher.runtime.AndroidJavaRuntimeManager
import com.mflegend.flamelauncher.runtime.JavaRuntimeSelectionPolicy

/**
 * A validated, data-only launch preparation result.
 *
 * This is not an executable process command: Flame still needs an Android JVM
 * bridge, native dependency loader, graphics/input/audio integration, account
 * authentication, and physical-device verification before it can start a game.
 */
data class MinecraftLaunchPreparation(
    val versionId: String,
    val mainClass: String,
    val requiredJavaMajorVersion: Int?,
    val selectedRuntimeId: String?,
    val classpathPlan: MinecraftLaunchClasspathPlan,
    val jvmArguments: List<String>,
    val gameArguments: List<String>,
    val blockers: List<String>
) {
    val hasExactRuntimeCandidate: Boolean
        get() = selectedRuntimeId != null
}

/** Combines validated version metadata, tokenized arguments, and exact runtime matching. */
object MinecraftLaunchPreparationBuilder {
    fun prepare(
        metadataBytes: ByteArray,
        expectedVersionId: String,
        ruleContext: MinecraftLaunchRuleContext,
        substitutions: Map<String, String>,
        installedRuntimes: List<AndroidJavaRuntimeManager.InstalledRuntime>,
        legacyJavaMajorVersion: Int? = null
    ): MinecraftLaunchPreparation {
        val metadata = MinecraftLaunchMetadataParser.parse(metadataBytes, expectedVersionId)
        val requiredJavaMajor = metadata.requiredJavaMajorVersion ?: legacyJavaMajorVersion
        if (metadata.requiredJavaMajorVersion == null && legacyJavaMajorVersion != null) {
            require(legacyJavaMajorVersion in 8..99) {
                "The fallback Java major version is invalid."
            }
        }

        val selectedRuntime = JavaRuntimeSelectionPolicy.findExactMajor(
            requiredJavaMajor,
            installedRuntimes
        )
        val classpathPlan = MinecraftLaunchClasspathPlanner.plan(
            metadataBytes,
            expectedVersionId,
            MojangLibraryRuleContext(
                platformName = ruleContext.platform.metadataName ?: "android",
                osVersion = ruleContext.osVersion,
                osArchitecture = ruleContext.osArchitecture,
                features = ruleContext.features.mapValues { it.value }
            )
        )

        val jvmArguments = if (metadata.hasModernGameArguments) {
            val root = org.json.JSONObject(metadataBytes.toString(Charsets.UTF_8))
            if (root.optJSONObject("arguments")?.has("jvm") == true) {
                MinecraftArgumentResolver.resolveJvmArguments(
                    metadataBytes,
                    expectedVersionId,
                    ruleContext,
                    substitutions
                )
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        val gameArguments = if (metadata.hasModernGameArguments) {
            MinecraftArgumentResolver.resolveGameArguments(
                metadataBytes,
                expectedVersionId,
                ruleContext,
                substitutions
            )
        } else {
            MinecraftArgumentResolver.resolveLegacyGameArguments(
                metadataBytes,
                expectedVersionId,
                substitutions
            )
        }

        val blockers = buildList {
            if (requiredJavaMajor == null) {
                add("The version does not declare a Java major; choose a verified legacy Java version.")
            } else if (selectedRuntime == null) {
                add("No imported runtime exactly matches Java $requiredJavaMajor and the app process ABI.")
            }
            if (classpathPlan.nativeLibrariesPending.isNotEmpty()) {
                add("Android native libraries still need compatible classifiers/extraction: " +
                    classpathPlan.nativeLibrariesPending.joinToString())
            }
            add("Flame Launcher has not loaded an Android-compatible JVM.")
            add("Android native dependencies and the graphics/input/audio bridge are not integrated.")
            add("Microsoft sign-in and Minecraft Java entitlement verification are not implemented.")
            add("A physical-device Minecraft gameplay test has not passed.")
        }

        return MinecraftLaunchPreparation(
            versionId = metadata.versionId,
            mainClass = metadata.mainClass,
            requiredJavaMajorVersion = requiredJavaMajor,
            selectedRuntimeId = selectedRuntime?.id,
            classpathPlan = classpathPlan,
            jvmArguments = jvmArguments,
            gameArguments = gameArguments,
            blockers = blockers
        )
    }
}
