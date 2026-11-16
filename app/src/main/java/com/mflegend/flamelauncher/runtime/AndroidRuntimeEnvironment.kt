package com.mflegend.flamelauncher.runtime

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Describes Android device capability relevant to a future arm64 Minecraft JVM.
 *
 * This is diagnostic only: it does not install, load, or start a JVM.
 */
data class AndroidRuntimeEnvironment(
    val androidApiLevel: Int,
    val primaryAbi: String?,
    val supportedAbis: List<String>,
    val is64BitProcess: Boolean,
    val memoryClassMb: Int?,
    val maxHeapMb: Long,
    val processorCount: Int,
    val supportsInitialArm64Runtime: Boolean
) {
    companion object {
        fun inspect(context: Context): AndroidRuntimeEnvironment {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val supportedAbis = Build.SUPPORTED_ABIS.toList()
            val arm64Device = Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")
            val is64BitProcess = Process.is64Bit()

            return AndroidRuntimeEnvironment(
                androidApiLevel = Build.VERSION.SDK_INT,
                primaryAbi = supportedAbis.firstOrNull(),
                supportedAbis = supportedAbis,
                is64BitProcess = is64BitProcess,
                memoryClassMb = activityManager?.memoryClass,
                maxHeapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L),
                processorCount = Runtime.getRuntime().availableProcessors(),
                supportsInitialArm64Runtime = arm64Device && is64BitProcess
            )
        }
    }
}
