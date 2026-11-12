package com.flame.launcher

/**
 * Snapshot of Android ABI selection and the architecture of this app process.
 *
 * The preferred Android ABI is used for runtime matching. The other fields are
 * diagnostic only: device CPU capability does not guarantee that this APK
 * contains or can load a native library for every listed ABI.
 */
internal data class DeviceAbiDiagnostics(
    val supportedAbis: List<String>,
    val supported64BitAbis: List<String>,
    val supported32BitAbis: List<String>,
    val processArchitecture: String?,
    val nativeLibraryDirectory: String?
) {
    val preferredAbi: String
        get() = supportedAbis.firstOrNull().orEmpty()
            .ifBlank { processArchitecture.orEmpty() }

    fun displayLines(): List<String> = listOf(
        "Android preferred ABIs: " + supportedAbis.displayValue(),
        "Android 64-bit ABIs: " + supported64BitAbis.displayValue(),
        "Android 32-bit ABIs: " + supported32BitAbis.displayValue(),
        "App process architecture: " + processArchitecture.displayValue(),
        "Native library directory: " + nativeLibraryDirectory.displayValue()
    )

    private fun List<String>.displayValue(): String =
        filter { it.isNotBlank() }.distinct().takeIf { it.isNotEmpty() }?.joinToString() ?: "Unavailable"

    private fun String?.displayValue(): String =
        this?.trim()?.takeIf { it.isNotEmpty() } ?: "Unavailable"
}
