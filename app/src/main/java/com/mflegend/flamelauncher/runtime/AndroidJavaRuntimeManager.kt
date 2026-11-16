package com.mflegend.flamelauncher.runtime

import android.content.Context
import java.io.File

/**
 * Inspects Java runtimes provisioned in Flame Launcher's private app storage.
 *
 * Runtime bundles must be installed under noBackupFilesDir/java-runtimes/<runtime-id>.
 * They must never be loaded directly from a SAF document URI: native libraries
 * need a stable app-private filesystem location.
 *
 * This class only validates files and metadata. It does not download, extract,
 * execute, or load native code.
 */
class AndroidJavaRuntimeManager(context: Context) {
    private val runtimeRoot = File(context.noBackupFilesDir, RUNTIME_DIRECTORY)

    data class InstalledRuntime(
        val id: String,
        val javaVersion: String,
        val javaMajorVersion: Int,
        val architecture: String,
        val homeDirectory: File,
        val jvmLibrary: File,
        val matchesProcessAbi: Boolean
    )

    data class ScanResult(
        val runtimes: List<InstalledRuntime>,
        val warnings: List<String>
    ) {
        val abiMatchedRuntimes: List<InstalledRuntime>
            get() = runtimes.filter { it.matchesProcessAbi }
    }

    fun scan(environment: AndroidRuntimeEnvironment): ScanResult {
        if (!runtimeRoot.exists()) {
            return ScanResult(emptyList(), listOf("No Android Java runtime has been installed."))
        }
        if (!runtimeRoot.isDirectory) {
            return ScanResult(emptyList(), listOf("The private Java runtime location is not a directory."))
        }

        val warnings = mutableListOf<String>()
        val runtimes = mutableListOf<InstalledRuntime>()
        val children = runtimeRoot.listFiles()
        if (children == null) {
            return ScanResult(emptyList(), listOf("The private Java runtime directory could not be read."))
        }

        for (directory in children) {
            if (!directory.isDirectory || !RUNTIME_ID.matches(directory.name)) continue
            val canonicalRoot = runtimeRoot.canonicalFile
            val canonicalHome = directory.canonicalFile
            if (canonicalHome.parentFile != canonicalRoot) {
                warnings += "Ignored runtime folder outside the managed directory: ${directory.name}"
                continue
            }

            val releaseFile = File(canonicalHome, "release")
            if (!isSafeChild(canonicalHome, releaseFile) || !releaseFile.isFile || releaseFile.length() > MAX_RELEASE_FILE_BYTES) {
                warnings += "Ignored ${directory.name}: Java release metadata is missing or invalid."
                continue
            }

            val properties = try {
                parseReleaseFile(releaseFile.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                warnings += "Ignored ${directory.name}: Java release metadata is invalid."
                continue
            }
            val version = properties["JAVA_VERSION"]
            val architecture = properties["OS_ARCH"]
            if (version.isNullOrBlank() || architecture.isNullOrBlank()) {
                warnings += "Ignored ${directory.name}: JAVA_VERSION or OS_ARCH is missing."
                continue
            }
            val major = parseJavaMajor(version)
            if (major == null || major < MINIMUM_JAVA_MAJOR) {
                warnings += "Ignored ${directory.name}: unsupported Java version metadata."
                continue
            }

            val jvm = findJvmLibrary(canonicalHome)
            if (jvm == null) {
                warnings += "Ignored ${directory.name}: libjvm.so is missing."
                continue
            }

            val normalizedArchitecture = normalizeArchitecture(architecture)
            val compatible = environment.is64BitProcess &&
                environment.supportedAbis.contains("arm64-v8a") &&
                normalizedArchitecture == "arm64"
            runtimes += InstalledRuntime(
                id = directory.name,
                javaVersion = version,
                javaMajorVersion = major,
                architecture = normalizedArchitecture,
                homeDirectory = canonicalHome,
                jvmLibrary = jvm,
                matchesProcessAbi = compatible
            )
        }

        return ScanResult(runtimes.sortedWith(compareByDescending<InstalledRuntime> { it.matchesProcessAbi }
            .thenByDescending { it.javaMajorVersion }), warnings)
    }

    private fun findJvmLibrary(home: File): File? {
        val candidates = listOf(
            File(home, "lib/server/libjvm.so"),
            File(home, "lib/aarch64/server/libjvm.so"),
            File(home, "lib/arm64/server/libjvm.so")
        )
        return candidates.firstOrNull { isSafeChild(home, it) && it.isFile && it.length() > 0L }
    }

    private fun isSafeChild(home: File, candidate: File): Boolean {
        val homePath = home.canonicalPath + File.separator
        return candidate.canonicalPath.startsWith(homePath)
    }

    private fun parseReleaseFile(content: String): Map<String, String> {
        val values = mutableMapOf<String, String>()
        for (line in content.lineSequence()) {
            val separator = line.indexOf('=')
            if (separator <= 0) continue
            val key = line.substring(0, separator).trim()
            val rawValue = line.substring(separator + 1).trim()
            if (!RELEASE_KEY.matches(key)) continue
            val value = rawValue.removeSurrounding("\"")
            if ('\u0000' !in value) values[key] = value
        }
        return values
    }

    private fun parseJavaMajor(version: String): Int? {
        val normalized = version.removePrefix("1.")
        return normalized.substringBefore('.').substringBefore('-').toIntOrNull()
    }

    private fun normalizeArchitecture(architecture: String): String = when (architecture.lowercase()) {
        "aarch64", "arm64", "arm64-v8a" -> "arm64"
        "x86_64", "amd64" -> "x86_64"
        "x86", "i386", "i686" -> "x86"
        "arm", "arm32", "armv7l" -> "arm"
        else -> architecture.lowercase()
    }

    companion object {
        private const val RUNTIME_DIRECTORY = "java-runtimes"
        private const val MINIMUM_JAVA_MAJOR = 8
        private const val MAX_RELEASE_FILE_BYTES = 64L * 1024L
        private val RUNTIME_ID = Regex("[A-Za-z0-9._-]{1,80}")
        private val RELEASE_KEY = Regex("[A-Z][A-Z0-9_]*")
    }
}
