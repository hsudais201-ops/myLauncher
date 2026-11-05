package com.flame.launcher.runtime

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * A snapshot of device facts that can be evaluated without Android framework dependencies.
 * This keeps compatibility rules deterministic and unit-testable.
 */
data class RuntimeDeviceSnapshot(
    val androidApiLevel: Int,
    val supportedAbis: List<String>,
    val totalMemoryBytes: Long? = null
)

enum class RuntimeCompatibilityIssue(val explanation: String) {
    ANDROID_API_TOO_OLD("This build requires Android API 26 or newer."),
    NO_SUPPORTED_CPU_ABI("No supported 32-bit or 64-bit CPU ABI was reported by Android.")
}

data class RuntimeCompatibilityReport(
    val compatible: Boolean,
    val androidApiLevel: Int,
    val supportedDeviceAbis: List<String>,
    val unsupportedDeviceAbis: List<String>,
    val primaryRuntimeAbi: String?,
    val totalMemoryBytes: Long?,
    val issues: List<RuntimeCompatibilityIssue>
)

/**
 * Current preflight ABI set follows the multi-architecture runtime targets documented by
 * AngelAuraMC/Amethyst-Android. This is only a device compatibility check; it does not mean
 * that a JRE, renderer, or Minecraft game installation is present.
 */
object RuntimeCompatibilityEvaluator {
    const val MIN_ANDROID_API_LEVEL = 26

    val engineSupportedAbis: List<String> = listOf(
        "arm64-v8a",
        "armeabi-v7a",
        "x86_64",
        "x86"
    )

    fun evaluate(snapshot: RuntimeDeviceSnapshot): RuntimeCompatibilityReport {
        val supportedOnDevice = snapshot.supportedAbis.filter { it in engineSupportedAbis }
        val unsupportedOnDevice = snapshot.supportedAbis.filterNot { it in engineSupportedAbis }
        val issues = buildList {
            if (snapshot.androidApiLevel < MIN_ANDROID_API_LEVEL) {
                add(RuntimeCompatibilityIssue.ANDROID_API_TOO_OLD)
            }
            if (supportedOnDevice.isEmpty()) {
                add(RuntimeCompatibilityIssue.NO_SUPPORTED_CPU_ABI)
            }
        }

        return RuntimeCompatibilityReport(
            compatible = issues.isEmpty(),
            androidApiLevel = snapshot.androidApiLevel,
            supportedDeviceAbis = supportedOnDevice,
            unsupportedDeviceAbis = unsupportedOnDevice,
            primaryRuntimeAbi = supportedOnDevice.firstOrNull(),
            totalMemoryBytes = snapshot.totalMemoryBytes?.takeIf { it > 0L },
            issues = issues
        )
    }
}

/** Reads real device information; policy decisions remain in RuntimeCompatibilityEvaluator. */
class AndroidRuntimeEnvironmentProvider(context: Context) {
    private val appContext = context.applicationContext

    fun snapshot(): RuntimeDeviceSnapshot {
        val activityManager =
            appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = activityManager?.let { manager ->
            ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        }

        return RuntimeDeviceSnapshot(
            androidApiLevel = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            totalMemoryBytes = memoryInfo?.totalMem
        )
    }
}
