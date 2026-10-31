package com.hyperlauncher.mobile

/**
 * Android-side runtime compatibility assessment.
 *
 * Android reports a Linux kernel/OS family, but that does not make desktop
 * Linux natives or a desktop JVM usable. This check deliberately considers
 * Android ABI and the presence of an Android-compatible runtime separately.
 */
data class AndroidRuntimeAssessment(
    val selectedAbi: String?,
    val runtimeInstalled: Boolean,
    val runtimeUsable: Boolean,
    val warning: String?
)

object AndroidRuntimeCompatibility {
    private val supportedRuntimeAbis = listOf("arm64-v8a", "x86_64")

    fun assess(
        androidApiLevel: Int,
        supportedAbis: List<String>,
        runtimeInstalled: Boolean
    ): AndroidRuntimeAssessment {
        require(androidApiLevel >= 1) { "Android API level must be positive" }
        val selectedAbi = supportedRuntimeAbis.firstOrNull { it in supportedAbis }
        val warning = when {
            androidApiLevel < 26 ->
                "Android 8.0 or newer is required by this launcher build."
            selectedAbi == null ->
                "No supported Android runtime ABI was detected. Supported ABIs: " +
                    supportedRuntimeAbis.joinToString()
            !runtimeInstalled ->
                "An Android-compatible Java runtime is not installed. Desktop Java is not sufficient."
            else -> null
        }
        return AndroidRuntimeAssessment(
            selectedAbi = selectedAbi,
            runtimeInstalled = runtimeInstalled,
            runtimeUsable = warning == null,
            warning = warning
        )
    }
}
