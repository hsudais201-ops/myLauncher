package com.flame.launcher

/**
 * Snapshot of Android ABI selection and the architecture of this app process.
 *
 * Runtime matching must use the architecture reported by the running process
 * when available. Build.SUPPORTED_ABIS describes device support and may start
 * with a 64-bit ABI even when this APK process is 32-bit.
 */
internal data class DeviceAbiDiagnostics(
    val supportedAbis: List<String>,
    val supported64BitAbis: List<String>,
    val supported32BitAbis: List<String>,
    val processArchitecture: String?,
    val nativeLibraryDirectory: String?
) {
    val preferredAbi: String
        get() = processArchitecture?.trim()?.takeIf { it.isNotEmpty() }
            ?: supportedAbis.firstNotNullOfOrNull { it.trim().takeIf(String::isNotEmpty) }
            ?: ""

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
