package com.mflegend.flamelauncher.runtime

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

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

    fun scan(environment: AndroidRuntimeEnvironment): ScanResult = try {
        scanInternal(environment)
    } catch (_: SecurityException) {
        ScanResult(emptyList(), listOf("Access to the private Java runtime directory was denied."))
    } catch (_: IOException) {
        ScanResult(emptyList(), listOf("The private Java runtime directory could not be inspected safely."))
    }

    private fun scanInternal(environment: AndroidRuntimeEnvironment): ScanResult {
        if (!runtimeRoot.exists()) {
            return ScanResult(emptyList(), listOf("No Android Java runtime has been installed."))
        }
        if (RuntimePathPolicy.isSymbolicLink(runtimeRoot)) {
            return ScanResult(emptyList(), listOf("The private Java runtime directory must not be a symbolic link."))
        }
        if (!runtimeRoot.isDirectory) {
            return ScanResult(emptyList(), listOf("The private Java runtime location is not a directory."))
        }

        val warnings = mutableListOf<String>()
        val runtimes = mutableListOf<InstalledRuntime>()
        val canonicalAppRoot = try {
            runtimeRoot.parentFile?.canonicalFile
        } catch (_: IOException) {
            return ScanResult(emptyList(), listOf("The app-private storage directory could not be resolved safely."))
        } catch (_: SecurityException) {
            return ScanResult(emptyList(), listOf("Access to app-private storage was denied."))
        }
        val canonicalRoot = try {
            runtimeRoot.canonicalFile
        } catch (_: IOException) {
            return ScanResult(emptyList(), listOf("The private Java runtime directory could not be resolved safely."))
        } catch (_: SecurityException) {
            return ScanResult(emptyList(), listOf("Access to the private Java runtime directory was denied."))
        }
        if (canonicalAppRoot == null || !RuntimePathPolicy.isDirectChild(canonicalAppRoot, canonicalRoot)) {
            return ScanResult(emptyList(), listOf("The Java runtime directory resolves outside its managed app-private location."))
        }
        val children = runtimeRoot.listFiles()
        if (children == null) {
            return ScanResult(emptyList(), listOf("The private Java runtime directory could not be read."))
        }

        for (directory in children) {
            if (RuntimePathPolicy.isSymbolicLink(directory)) {
                warnings += "Ignored ${directory.name}: symbolic-link runtime folders are not allowed."
                continue
            }
            if (!directory.isDirectory || !RuntimePathPolicy.isRuntimeId(directory.name)) continue
            val canonicalHome = try {
                directory.canonicalFile
            } catch (_: IOException) {
                warnings += "Ignored ${directory.name}: runtime path could not be resolved safely."
                continue
            } catch (_: SecurityException) {
                warnings += "Ignored ${directory.name}: access to the runtime path was denied."
                continue
            }
            if (!RuntimePathPolicy.isDirectChild(canonicalRoot, canonicalHome)) {
                warnings += "Ignored runtime folder outside the managed directory: ${directory.name}"
                continue
            }

            val releaseFile = File(canonicalHome, "release")
            if (RuntimePathPolicy.isSymbolicLink(releaseFile) ||
                !RuntimePathPolicy.isSafeChild(canonicalHome, releaseFile) ||
                !releaseFile.isFile ||
                releaseFile.length() > MAX_RELEASE_FILE_BYTES
            ) {
                warnings += "Ignored ${directory.name}: Java release metadata is missing or invalid."
                continue
            }

            val properties = try {
                JavaRuntimeMetadata.parseReleaseFile(readBoundedText(releaseFile, MAX_RELEASE_FILE_BYTES))
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
            val major = JavaRuntimeMetadata.parseJavaMajor(version)
            if (major == null || major < MINIMUM_JAVA_MAJOR) {
                warnings += "Ignored ${directory.name}: unsupported Java version metadata."
                continue
            }

            val normalizedArchitecture = JavaRuntimeMetadata.normalizeArchitecture(architecture)
            if (normalizedArchitecture != "arm64") {
                warnings += "Ignored ${directory.name}: only ARM64 Java runtimes are supported by the current integration target."
                continue
            }

            val jvm = findJvmLibrary(canonicalHome, normalizedArchitecture)
            if (jvm == null) {
                warnings += "Ignored ${directory.name}: a valid AArch64 ELF64 libjvm.so was not found."
                continue
            }

            val compatible = JavaRuntimeMetadata.matchesInitialArm64Process(
                normalizedArchitecture,
                environment.is64BitProcess,
                environment.supportedAbis
            )
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

    /** Reads no more than the configured limit, even if the file grows during scanning. */
    private fun readBoundedText(file: File, maxBytes: Long): String {
        val output = ByteArrayOutputStream()
        file.inputStream().use { input ->
            val buffer = ByteArray(4096)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) throw IOException("Runtime release file exceeds size limit")
                output.write(buffer, 0, count)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun findJvmLibrary(home: File, normalizedArchitecture: String): File? {
        val candidates = listOf(
            File(home, "lib/server/libjvm.so"),
            File(home, "lib/aarch64/server/libjvm.so"),
            File(home, "lib/arm64/server/libjvm.so")
        )
        return candidates.firstOrNull {
            try {
                !RuntimePathPolicy.isSymbolicLink(it) &&
                    RuntimePathPolicy.isSafeChild(home, it) &&
                    it.isFile &&
                    it.length() > 0L &&
                    (normalizedArchitecture != "arm64" || RuntimeBinaryPolicy.isArm64Elf64(it))
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }

    companion object {
        private const val RUNTIME_DIRECTORY = "java-runtimes"
        private const val MINIMUM_JAVA_MAJOR = 8
        private const val MAX_RELEASE_FILE_BYTES = 64L * 1024L
    }
}
