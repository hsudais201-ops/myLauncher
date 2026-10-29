package dev.mylauncher

import android.os.Build

/** Reports device/runtime facts for diagnostics; this does not launch Minecraft. */
object RuntimeDiagnostics {
    fun summary(): String {
        val androidApi = Build.VERSION.SDK_INT
        val supportedAbis = Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { "unknown" }
        val javaVersion = System.getProperty("java.version", "unknown")
        val architecture = System.getProperty("os.arch", "unknown")
        return "Android API $androidApi; ABIs: $supportedAbis; Java $javaVersion; JVM architecture: $architecture"
    }
}
