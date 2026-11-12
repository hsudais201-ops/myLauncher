package com.flame.launcher.runtime

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CancellationException

/**
 * Installs a pair of already-authenticated Mojo runtime components.
 *
 * The component signatures are checked by MojoRuntimeComponentDownloader.
 * This class independently re-hashes both staged files before extraction so a
 * mutable DownloadedMojoRuntimePackage cannot be used to substitute archives.
 * Universal is extracted first; the ABI component overlays it second.
 */
class MojoRuntimePackageInstaller(
    runtimeRoot: File,
    deviceArchitecture: String,
    private val extractor: MojoRuntimeTarXzExtractor = MojoRuntimeTarXzExtractor()
) {
    private val root = runtimeRoot.canonicalFile
    private val targetArchitecture = JavaRuntimeManager.normalizeArchitecture(deviceArchitecture)
        ?: throw IllegalArgumentException("Device architecture is unsupported.")

    @Synchronized
    fun install(
        downloaded: DownloadedMojoRuntimePackage,
        onProgress: (MojoRuntimeInstallProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): JavaRuntimeInstallation {
        require(downloaded.javaMajorVersion in SUPPORTED_JAVA_MAJORS) {
            "Runtime Java major version is unsupported."
        }
        val architecture = JavaRuntimeManager.normalizeArchitecture(downloaded.architecture)
            ?: throw IllegalArgumentException("Runtime package architecture is unsupported.")
        require(architecture == targetArchitecture) {
            "Runtime package architecture does not match this device."
        }
        require(RUNTIME_ID.matches(runtimeId(downloaded))) {
            "Runtime package identifier is invalid."
        }
        if (!root.exists()) check(root.mkdirs()) { "Unable to create the private runtime directory." }
        require(root.isDirectory) { "Runtime destination is not a directory." }

        val packageDirectory = downloaded.directory.canonicalFile
        require(packageDirectory.isDirectory) { "Downloaded runtime package directory is missing." }
        val universal = verifyComponent(
            downloaded.universalComponent,
            packageDirectory,
            "universal.tar.xz"
        )
        val platform = verifyComponent(
            downloaded.platformComponent,
            packageDirectory,
            "bin-$architecture.tar.xz"
        )

        val id = runtimeId(downloaded)
        val staging = File(root, ".mojo-install-$id-${UUID.randomUUID()}")
        check(staging.mkdir()) { "Unable to create runtime installation staging directory." }
        val extractionRoot = File(staging, "payload")
        check(extractionRoot.mkdir()) { "Unable to create runtime extraction directory." }

        try {
            checkCancelled(isCancelled)
            onProgress(MojoRuntimeInstallProgress.Extracting("universal.tar.xz", 0L))
            val universalResult = extractor.extract(
                universal,
                extractionRoot,
                "universal.tar.xz",
                onProgress = { component, bytes, _ ->
                    onProgress(MojoRuntimeInstallProgress.Extracting(component, bytes))
                },
                isCancelled = isCancelled
            )
            checkCancelled(isCancelled)
            val platformResult = extractor.extract(
                platform,
                extractionRoot,
                "bin-$architecture.tar.xz",
                onProgress = { component, bytes, _ ->
                    onProgress(MojoRuntimeInstallProgress.Extracting(component, bytes))
                },
                isCancelled = isCancelled
            )
            checkCancelled(isCancelled)

            val runtimeHome = findRuntimeHome(extractionRoot)
            validateRuntime(runtimeHome, downloaded.javaMajorVersion, architecture)

            val destination = File(root, id)
            require(destination.canonicalFile.parentFile == root) {
                "Runtime destination resolves outside the private runtime directory."
            }
            val backup = if (destination.exists() || Files.isSymbolicLink(destination.toPath())) {
                val backupFile = File(root, ".mojo-backup-$id-${UUID.randomUUID()}")
                check(destination.renameTo(backupFile)) {
                    "Unable to stage the existing runtime for replacement."
                }
                backupFile
            } else null

            var promoted = false
            val installed: JavaRuntimeInstallation
            try {
                check(runtimeHome.renameTo(destination)) {
                    "Unable to promote the validated Mojo runtime."
                }
                promoted = true
                installed = JavaRuntimeManager(root, targetArchitecture)
                    .discover()
                    .firstOrNull { it.id == id }
                    ?: throw IllegalStateException("Installed Mojo runtime failed final validation.")
                require(installed.majorVersion == downloaded.javaMajorVersion &&
                    installed.architecture == architecture &&
                    installed.jvmLibrary?.isFile == true
                ) { "Installed Mojo runtime metadata changed during promotion." }
            } catch (failure: Exception) {
                if (promoted) destination.deleteRecursively()
                if (backup != null && backup.exists() && !backup.renameTo(destination)) {
                    failure.addSuppressed(
                        IllegalStateException("The previous Mojo runtime could not be restored.")
                    )
                }
                throw failure
            }

            // Promotion is committed now. A UI/progress observer must not be able
            // to turn a successful install into a reported failure or trigger rollback.
            backup?.deleteRecursively()
            try {
                onProgress(
                    MojoRuntimeInstallProgress.Completed(
                        installed,
                        universalResult.expandedBytes + platformResult.expandedBytes
                    )
                )
            } catch (_: Exception) {
                // Completion notifications are best-effort after the commit point.
            }
            return installed
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun verifyComponent(
        component: VerifiedMojoRuntimeComponent,
        packageDirectory: File,
        expectedName: String
    ): File {
        require(component.sizeBytes in 1..MAX_COMPONENT_BYTES) {
            "Runtime component size metadata is invalid."
        }
        require(SHA256.matches(component.sha256)) {
            "Runtime component SHA-256 metadata is invalid."
        }
        val file = component.file.canonicalFile
        require(file.parentFile == packageDirectory && file.name == expectedName && file.isFile) {
            "Runtime component is not in the expected authenticated package directory."
        }
        require(file.length() == component.sizeBytes) {
            "Runtime component size changed after signature verification."
        }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual.equals(component.sha256, ignoreCase = true)) {
            "Runtime component changed after signature verification."
        }
        return file
    }

    private fun findRuntimeHome(extractionRoot: File): File {
        if (File(extractionRoot, "release").isFile) return extractionRoot
        val candidates = extractionRoot.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "release").isFile }
        require(candidates.size == 1) {
            "Runtime archives must contain one unambiguous Java runtime root."
        }
        val home = candidates.single().canonicalFile
        require(home.parentFile == extractionRoot.canonicalFile) {
            "Runtime root resolves outside the extraction directory."
        }
        return home
    }

    private fun validateRuntime(home: File, expectedMajor: Int, expectedArchitecture: String) {
        require(home.isDirectory && home.canonicalFile == home) {
            "Runtime home is not a safe directory."
        }
        val release = File(home, "release").canonicalFile
        require(release.parentFile == home && release.isFile &&
            release.length() in 1..MAX_RELEASE_BYTES.toLong()
        ) { "Runtime release metadata is missing or invalid." }
        val properties = Properties()
        FileInputStream(release).use { properties.load(it) }
        val version = properties.getProperty("JAVA_VERSION")
            ?.trim()?.trim('"')
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Runtime release metadata has no Java version.")
        val major = JavaRuntimeManager.parseMajorVersion(version)
            ?: throw IllegalArgumentException("Runtime Java version is invalid.")
        require(major == expectedMajor) {
            "Runtime Java major version does not match the selected package."
        }
        val architecture = JavaRuntimeManager.normalizeArchitecture(
            properties.getProperty("OS_ARCH")?.trim()?.trim('"').orEmpty()
        ) ?: throw IllegalArgumentException("Runtime architecture metadata is missing or unsupported.")
        require(architecture == expectedArchitecture) {
            "Runtime architecture metadata does not match this device."
        }

        val jvm = JVM_LIBRARY_PATHS.asSequence()
            .map { File(home, it).canonicalFile }
            .firstOrNull { it.path.startsWith(home.canonicalPath + File.separator) && it.isFile }
        require(jvm != null) { "Runtime package is missing its embedded JVM shared library." }

        val java = File(home, "bin/java")
        if (java.exists()) {
            require(java.canonicalPath.startsWith(home.canonicalPath + File.separator) && java.isFile) {
                "Runtime Java executable is invalid."
            }
            if (!java.canExecute()) {
                require(java.setExecutable(true, false) || java.canExecute()) {
                    "Unable to mark the runtime Java executable."
                }
            }
        }
    }

    private fun runtimeId(packageData: DownloadedMojoRuntimePackage): String =
        "mojo-java-${packageData.javaMajorVersion}-${JavaRuntimeManager.normalizeArchitecture(packageData.architecture) ?: "unknown"}"

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw CancellationException("Mojo runtime installation cancelled.")
    }

    companion object {
        private const val MAX_COMPONENT_BYTES = 512L * 1024 * 1024
        private const val MAX_RELEASE_BYTES = 32 * 1024
        private const val BUFFER_SIZE = 32 * 1024
        private val SUPPORTED_JAVA_MAJORS = setOf(17, 21, 25)
        private val RUNTIME_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        private val SHA256 = Regex("[a-fA-F0-9]{64}")
        private val JVM_LIBRARY_PATHS = listOf(
            "lib/server/libjvm.so",
            "lib/client/libjvm.so",
            "lib/aarch64/server/libjvm.so",
            "lib/arm64/server/libjvm.so",
            "lib/amd64/server/libjvm.so",
            "lib/x86_64/server/libjvm.so"
        )
    }
}

sealed interface MojoRuntimeInstallProgress {
    data class Extracting(val component: String, val expandedBytes: Long) : MojoRuntimeInstallProgress
    data class Completed(
        val installation: JavaRuntimeInstallation,
        val expandedBytes: Long
    ) : MojoRuntimeInstallProgress
}
