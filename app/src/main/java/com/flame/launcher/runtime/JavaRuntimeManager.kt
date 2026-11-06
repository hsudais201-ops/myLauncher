package com.flame.launcher.runtime

import java.io.File
import java.io.FileInputStream
import java.util.Properties

/**
 * Discovers Java runtimes installed inside Flame's private runtime directory.
 *
 * A runtime is accepted only when its release metadata, architecture and JVM
 * entry point are present and remain inside the managed runtime directory.
 * This class does not download a JRE or execute it; launching an Android JVM
 * requires the platform-specific native bridge.
 */
class JavaRuntimeManager(
    runtimeRoot: File,
    deviceArchitecture: String
) {
    private val root = runtimeRoot.canonicalFile
    private val targetArchitecture = normalizeArchitecture(deviceArchitecture) ?: "unknown"

    fun discover(): List<JavaRuntimeInstallation> {
        if (!root.isDirectory) return emptyList()
        val children = root.listFiles() ?: return emptyList()
        return children.asSequence()
            .filter { it.isDirectory && ID_PATTERN.matches(it.name) }
            .mapNotNull(::readInstallation)
            .sortedBy { it.id }
            .toList()
    }

    fun resolve(requiredMajorVersion: Int): JavaRuntimeResolution {
        require(requiredMajorVersion in 1..100) { "Required Java major version is invalid." }
        val candidates = discover().filter {
            it.majorVersion == requiredMajorVersion &&
                it.architecture == targetArchitecture
        }
        val selected = candidates.maxWithOrNull(Comparator { left, right ->
            compareVersions(left.version, right.version).takeIf { it != 0 }
                ?: left.id.compareTo(right.id)
        })
        return if (selected != null) {
            JavaRuntimeResolution.Available(selected)
        } else {
            val availableMajors = discover()
                .filter { it.architecture == targetArchitecture }
                .map { it.majorVersion }
                .distinct()
                .sorted()
            JavaRuntimeResolution.Unavailable(
                requiredMajorVersion = requiredMajorVersion,
                deviceArchitecture = targetArchitecture,
                availableMajors = availableMajors
            )
        }
    }

    private fun readInstallation(directory: File): JavaRuntimeInstallation? = try {
        val home = directory.canonicalFile
        if (home.parentFile != root || !home.isDirectory) return null

        val releaseFile = safeChild(home, "release") ?: return null
        if (!releaseFile.isFile || releaseFile.length() !in 1..MAX_RELEASE_FILE_BYTES.toLong()) {
            return null
        }

        val properties = Properties()
        FileInputStream(releaseFile).use { properties.load(it) }
        val version = properties.getProperty("JAVA_VERSION")
            ?.trim()
            ?.trim('"')
            ?.takeIf(String::isNotBlank)
            ?: return null
        val major = parseMajorVersion(version) ?: return null
        val architecture = normalizeArchitecture(
            properties.getProperty("OS_ARCH")?.trim()?.trim('"').orEmpty()
        ) ?: return null

        val executable = sequenceOf("bin/java", "bin/javaw")
            .mapNotNull { safeChild(home, it) }
            .firstOrNull { it.isFile && it.canExecute() }
        val jvmLibrary = JVM_LIBRARY_PATHS
            .asSequence()
            .mapNotNull { safeChild(home, it) }
            .firstOrNull { it.isFile }

        if (executable == null && jvmLibrary == null) return null

        JavaRuntimeInstallation(
            id = home.name,
            version = version,
            majorVersion = major,
            architecture = architecture,
            homeDirectory = home,
            javaExecutable = executable,
            jvmLibrary = jvmLibrary
        )
    } catch (_: Exception) {
        null
    }

    private fun safeChild(parent: File, relativePath: String): File? = try {
        if (relativePath.startsWith("/") || '\\' in relativePath) return null
        val candidate = File(parent, relativePath).canonicalFile
        val prefix = parent.canonicalPath + File.separator
        candidate.takeIf { it.path.startsWith(prefix) }
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val MAX_RELEASE_FILE_BYTES = 32 * 1024
        private val ID_PATTERN = Regex("[A-Za-z0-9._-]{1,64}")
        private val JVM_LIBRARY_PATHS = listOf(
            "lib/server/libjvm.so",
            "lib/client/libjvm.so",
            "lib/aarch64/server/libjvm.so",
            "lib/arm64/server/libjvm.so",
            "lib/amd64/server/libjvm.so",
            "lib/x86_64/server/libjvm.so"
        )

        internal fun parseMajorVersion(rawVersion: String): Int? {
            val normalized = rawVersion.trim().trim('"')
                .substringBefore('-')
                .substringBefore('+')
            val majorToken = if (normalized.startsWith("1.")) {
                normalized.removePrefix("1.").substringBefore('.')
            } else {
                normalized.substringBefore('.')
            }
            return majorToken.toIntOrNull()?.takeIf { it in 1..100 }
        }

        internal fun normalizeArchitecture(rawArchitecture: String): String? =
            when (rawArchitecture.trim().lowercase()) {
                "aarch64", "arm64", "arm64-v8a" -> "arm64"
                "arm", "armv7l", "armv8l", "armeabi-v7a" -> "arm"
                "amd64", "x86_64" -> "x86_64"
                "i386", "i486", "i586", "i686", "x86" -> "x86"
                else -> null
            }

        private fun compareVersions(left: String, right: String): Int {
            val leftParts = left.split(Regex("[^0-9]+")).mapNotNull { it.toIntOrNull() }
            val rightParts = right.split(Regex("[^0-9]+")).mapNotNull { it.toIntOrNull() }
            for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
                val leftPart = leftParts.getOrElse(index) { 0 }
                val rightPart = rightParts.getOrElse(index) { 0 }
                if (leftPart != rightPart) return leftPart.compareTo(rightPart)
            }
            return 0
        }
    }
}

data class JavaRuntimeInstallation(
    val id: String,
    val version: String,
    val majorVersion: Int,
    val architecture: String,
    val homeDirectory: File,
    val javaExecutable: File?,
    val jvmLibrary: File?
) {
    val executionKind: JavaRuntimeExecutionKind
        get() = if (jvmLibrary != null) JavaRuntimeExecutionKind.EMBEDDED_JVM
        else JavaRuntimeExecutionKind.PROCESS
}

enum class JavaRuntimeExecutionKind {
    EMBEDDED_JVM,
    PROCESS
}

sealed interface JavaRuntimeResolution {
    data class Available(val installation: JavaRuntimeInstallation) : JavaRuntimeResolution

    data class Unavailable(
        val requiredMajorVersion: Int,
        val deviceArchitecture: String,
        val availableMajors: List<Int>
    ) : JavaRuntimeResolution
}
