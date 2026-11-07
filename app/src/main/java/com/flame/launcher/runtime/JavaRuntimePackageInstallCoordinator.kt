package com.flame.launcher.runtime

import java.io.File
import java.io.FileInputStream
import java.util.UUID

/**
 * Coordinates a catalog-selected runtime download and transactional installation.
 *
 * The caller must only pass descriptors parsed from an independently trusted
 * catalog and must construct the downloader with the same trusted host policy.
 * Run install() on an IO/worker thread. The temporary verified archive is removed
 * whether installation succeeds or fails.
 */
class JavaRuntimePackageInstallCoordinator(
    private val downloader: JavaRuntimePackageDownloader,
    private val installer: JavaRuntimePackageInstaller,
    private val downloadDirectory: File,
    deviceArchitecture: String
) {
    private val targetArchitecture = JavaRuntimeManager.normalizeArchitecture(deviceArchitecture)
        ?: throw IllegalArgumentException("Device architecture is unsupported.")
    fun install(
        descriptor: JavaRuntimePackageDescriptor,
        onProgress: (JavaRuntimeInstallProgress) -> Unit = {}
    ): JavaRuntimeInstallation {
        require(descriptor.archiveType == "zip") {
            "Only ZIP Java runtime packages can be installed."
        }
        require(descriptor.id.matches(ID_PATTERN)) { "Runtime package ID is invalid." }
        require(descriptor.javaMajorVersion in 1..100) {
            "Runtime package Java major version is invalid."
        }
        require(descriptor.sizeBytes in 1..MAX_PACKAGE_BYTES) {
            "Runtime package size is invalid."
        }
        require(descriptor.sha256.matches(SHA256_PATTERN)) {
            "Runtime package SHA-256 is invalid."
        }
        val architecture = JavaRuntimeManager.normalizeArchitecture(descriptor.architecture)
            ?: throw IllegalArgumentException("Runtime package architecture is unsupported.")
        require(architecture == targetArchitecture) {
            "Runtime package architecture does not match this device."
        }

        if (!downloadDirectory.exists()) {
            check(downloadDirectory.mkdirs()) {
                "Unable to create the temporary runtime download directory."
            }
        }
        require(downloadDirectory.isDirectory) {
            "Runtime download destination is not a directory."
        }

        val archive = File(
            downloadDirectory,
            ".runtime-${descriptor.id}-${UUID.randomUUID()}.zip"
        )
        try {
            onProgress(JavaRuntimeInstallProgress.Downloading(descriptor.id))
            val downloaded = downloader.download(
                address = descriptor.downloadUrl,
                expectedSha256 = descriptor.sha256,
                destination = archive,
                expectedSizeBytes = descriptor.sizeBytes,
                onProgress = { received, total ->
                    onProgress(
                        JavaRuntimeInstallProgress.DownloadProgress(
                            descriptor.id,
                            received,
                            total ?: descriptor.sizeBytes
                        )
                    )
                }
            )
            require(downloaded.length() == descriptor.sizeBytes) {
                "Downloaded runtime size does not match the trusted catalog."
            }

            onProgress(JavaRuntimeInstallProgress.Installing(descriptor.id))
            val installation = FileInputStream(downloaded).use { input ->
                installer.install(
                    id = descriptor.id,
                    archiveInput = input,
                    expectedSha256 = descriptor.sha256,
                    expectedMajorVersion = descriptor.javaMajorVersion,
                    expectedArchitecture = architecture
                )
            }
            onProgress(JavaRuntimeInstallProgress.Completed(installation))
            return installation
        } finally {
            archive.delete()
        }
    }

    companion object {
        private const val MAX_PACKAGE_BYTES = 1024L * 1024 * 1024
        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        private val SHA256_PATTERN = Regex("[a-fA-F0-9]{64}")
    }
}

sealed interface JavaRuntimeInstallProgress {
    data class Downloading(val packageId: String) : JavaRuntimeInstallProgress
    data class DownloadProgress(
        val packageId: String,
        val receivedBytes: Long,
        val totalBytes: Long?
    ) : JavaRuntimeInstallProgress
    data class Installing(val packageId: String) : JavaRuntimeInstallProgress
    data class Completed(val installation: JavaRuntimeInstallation) : JavaRuntimeInstallProgress
}
