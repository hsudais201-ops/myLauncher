package com.hyperlauncher.mobile

import java.io.File

/**
 * Read-only inspection of app-private Android Java runtimes.
 * It follows the multi-runtime directory convention used by Amethyst/Pojav:
 * runtime folder/release and runtime folder/bin/java.
 *
 * This check does not execute Java and does not certify graphics integration.
 */
data class AndroidRuntimeDescriptor(
    val name: String,
    val javaMajor: Int,
    val architecture: String
)

data class AndroidRuntimeInventoryResult(
    val compatibleRuntime: AndroidRuntimeDescriptor?,
    val discoveredRuntimeCount: Int
)

object AndroidRuntimeInventory {
    fun inspect(runtimeRoot: File, deviceAbi: String?): AndroidRuntimeInventoryResult {
        if (deviceAbi == null || !runtimeRoot.isDirectory) {
            return AndroidRuntimeInventoryResult(null, 0)
        }
        val directories = runtimeRoot.listFiles()?.filter { it.isDirectory }.orEmpty()
        val compatible = directories.mapNotNull { directory ->
            readRuntime(directory, deviceAbi)
        }.maxByOrNull { it.javaMajor }
        return AndroidRuntimeInventoryResult(compatible, directories.size)
    }

    private fun readRuntime(directory: File, deviceAbi: String): AndroidRuntimeDescriptor? {
        val release = File(directory, "release")
        val java = File(directory, "bin/java")
        if (!release.isFile || !java.isFile || !java.canExecute()) return null
        val metadata = runCatching { release.readText() }.getOrNull() ?: return null
        val version = Regex("""(?m)^JAVA_VERSION="([^"]+)"$""")
            .find(metadata)?.groupValues?.getOrNull(1) ?: return null
        val architecture = Regex("""(?m)^OS_ARCH="([^"]+)"$""")
            .find(metadata)?.groupValues?.getOrNull(1) ?: return null
        val major = parseMajor(version) ?: return null
        if (!matchesAbi(deviceAbi, architecture)) return null
        return AndroidRuntimeDescriptor(directory.name, major, architecture)
    }

    private fun parseMajor(version: String): Int? {
        val pieces = version.split('.')
        val major = if (pieces.firstOrNull() == "1") pieces.getOrNull(1) else pieces.firstOrNull()
        return major?.takeWhile(Char::isDigit)?.toIntOrNull()?.takeIf { it > 0 }
    }

    private fun matchesAbi(deviceAbi: String, architecture: String): Boolean {
        val normalized = architecture.lowercase().replace("-", "").replace("_", "")
        return when (deviceAbi) {
            "arm64-v8a" -> normalized == "aarch64" || normalized == "arm64"
            "x86_64" -> normalized == "amd64" || normalized == "x8664"
            else -> false
        }
    }
}
