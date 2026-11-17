package com.mflegend.flamelauncher.runtime

/**
 * Explicit evidence gates required before Flame Launcher may enable Minecraft Play.
 *
 * These flags must represent verified implementation/device-test results, not
 * metadata inspection or ABI guesses. Defaults are deliberately fail-closed.
 */
data class RuntimeLaunchReadiness(
    val compatibleJvmLoaded: Boolean = false,
    val nativeDependenciesResolved: Boolean = false,
    val graphicsSurfaceReady: Boolean = false,
    val inputAndAudioReady: Boolean = false,
    val lifecycleHandlingReady: Boolean = false,
    val microsoftAccountAuthenticated: Boolean = false,
    val minecraftEntitlementVerified: Boolean = false,
    val physicalDeviceGameTestPassed: Boolean = false
) {
    val canLaunch: Boolean
        get() = compatibleJvmLoaded &&
            nativeDependenciesResolved &&
            graphicsSurfaceReady &&
            inputAndAudioReady &&
            lifecycleHandlingReady &&
            microsoftAccountAuthenticated &&
            minecraftEntitlementVerified &&
            physicalDeviceGameTestPassed

    val missingRequirements: List<String>
        get() = buildList {
            if (!compatibleJvmLoaded) add("Android-compatible Java runtime has not been loaded successfully")
            if (!nativeDependenciesResolved) add("Required native dependencies have not been resolved")
            if (!graphicsSurfaceReady) add("Graphics renderer and Android surface are not ready")
            if (!inputAndAudioReady) add("Touch, keyboard, and audio integration are not verified")
            if (!lifecycleHandlingReady) add("Pause, resume, and shutdown handling are not verified")
            if (!microsoftAccountAuthenticated) add("Microsoft/Xbox account authentication has not been completed")
            if (!minecraftEntitlementVerified) add("Minecraft Java ownership entitlement has not been verified")
            if (!physicalDeviceGameTestPassed) add("A real Minecraft session has not passed on a physical Android device")
        }
}
