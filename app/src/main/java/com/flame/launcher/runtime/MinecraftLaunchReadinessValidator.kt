package com.flame.launcher.runtime

import com.flame.launcher.content.MinecraftInstanceLaunchSettings
import com.flame.launcher.content.MinecraftInstanceProfile

/**
 * Fail-closed preflight for the profile/runtime portion of a Minecraft launch.
 *
 * A Ready result means only that profile metadata, launch preferences, Mojang's
 * Java requirement and an installed ABI-compatible runtime agree. It does not
 * prove that game artifacts are installed or that the JVM/renderer can run.
 */
class MinecraftLaunchReadinessValidator(
    private val runtimeManager: JavaRuntimeManager
) {
    fun validate(
        profile: MinecraftInstanceProfile,
        settings: MinecraftInstanceLaunchSettings,
        plan: MinecraftLaunchDependencyPlan
    ): MinecraftLaunchReadiness {
        if (profile.minecraftVersion != plan.versionId) {
            return MinecraftLaunchReadiness.Blocked.ProfileVersionMismatch(
                profileVersion = profile.minecraftVersion,
                planVersion = plan.versionId
            )
        }

        settings.validationError()?.let {
            return MinecraftLaunchReadiness.Blocked.InvalidSettings(it)
        }

        val requiredMajor = plan.requiredJavaMajorVersion
            ?.takeIf { it in 1..100 }
            ?: return MinecraftLaunchReadiness.Blocked.VersionRequirementUnavailable

        return when (val resolution = runtimeManager.resolve(requiredMajor, settings.javaRuntimeId)) {
            is JavaRuntimeResolution.Available ->
                MinecraftLaunchReadiness.Ready(profile, resolution.installation, plan)
            is JavaRuntimeResolution.Unavailable ->
                MinecraftLaunchReadiness.Blocked.RuntimeUnavailable(
                    requiredMajorVersion = resolution.requiredMajorVersion,
                    deviceArchitecture = resolution.deviceArchitecture,
                    availableMajors = resolution.availableMajors
                )
            is JavaRuntimeResolution.SelectionUnavailable ->
                MinecraftLaunchReadiness.Blocked.PreferredRuntimeUnavailable(
                    requiredMajorVersion = resolution.requiredMajorVersion,
                    preferredRuntimeId = resolution.preferredRuntimeId,
                    deviceArchitecture = resolution.deviceArchitecture,
                    availableMajors = resolution.availableMajors
                )
        }
    }
}

sealed interface MinecraftLaunchReadiness {
    data class Ready(
        val profile: MinecraftInstanceProfile,
        val runtime: JavaRuntimeInstallation,
        val dependencyPlan: MinecraftLaunchDependencyPlan
    ) : MinecraftLaunchReadiness

    sealed interface Blocked : MinecraftLaunchReadiness {
        data class ProfileVersionMismatch(
            val profileVersion: String,
            val planVersion: String
        ) : Blocked

        data class InvalidSettings(val reason: String) : Blocked

        data object VersionRequirementUnavailable : Blocked

        data class RuntimeUnavailable(
            val requiredMajorVersion: Int,
            val deviceArchitecture: String,
            val availableMajors: List<Int>
        ) : Blocked

        data class PreferredRuntimeUnavailable(
            val requiredMajorVersion: Int,
            val preferredRuntimeId: String,
            val deviceArchitecture: String,
            val availableMajors: List<Int>
        ) : Blocked
    }
}
