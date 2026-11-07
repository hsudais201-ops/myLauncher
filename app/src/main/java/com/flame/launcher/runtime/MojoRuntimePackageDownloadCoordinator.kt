package com.flame.launcher.runtime

import java.io.File
import java.security.PublicKey
import java.util.UUID
import java.util.concurrent.CancellationException

/**
 * Authenticates and stages the two Mojo runtime components as one package.
 * The destination directory is replaced only after both signatures pass.
 */
class MojoRuntimePackageDownloadCoordinator(
    private val componentDownloader: MojoRuntimeComponentDownloader =
        MojoRuntimeComponentDownloader()
) {
    fun download(
        descriptor: MojoRuntimePackageDescriptor,
        publicKey: PublicKey,
        destinationDirectory: File,
        onProgress: (component: String, receivedBytes: Long, totalBytes: Long?) -> Unit =
            { _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): DownloadedMojoRuntimePackage {
        require(descriptor.javaMajorVersion in SUPPORTED_JAVA_MAJORS) {
            "Runtime Java major version is unsupported."
        }
        val architecture = JavaRuntimeManager.normalizeArchitecture(descriptor.architecture)
            ?: throw IllegalArgumentException("Runtime package architecture is unsupported.")
        require(descriptor.component == "bin-$architecture.tar.xz") {
            "Runtime package component does not match its architecture."
        }
        require(descriptor.universalSignature.size == SIGNATURE_BYTES &&
            descriptor.signature.size == SIGNATURE_BYTES
        ) { "Runtime package signatures are invalid." }

        val destination = destinationDirectory.canonicalFile
        val parent = destination.parentFile
            ?: throw IllegalArgumentException("Runtime package destination has no parent.")
        if (!parent.exists()) {
            check(parent.mkdirs()) { "Unable to create runtime package directory." }
        }
        require(parent.isDirectory && destination.parentFile.canonicalFile == parent) {
            "Runtime package destination is invalid."
        }
        val staging = File(parent, ".mojo-stage-${UUID.randomUUID()}")
        check(staging.mkdir()) { "Unable to create runtime package staging directory." }

        try {
            if (isCancelled()) throw CancellationException("Runtime package download cancelled.")
            val universal = componentDownloader.download(
                address = descriptor.universalDownloadUrl,
                expectedSignature = descriptor.universalSignature,
                publicKey = publicKey,
                destination = File(staging, "universal.tar.xz"),
                onProgress = { received, total ->
                    onProgress("universal.tar.xz", received, total)
                },
                isCancelled = isCancelled
            )
            if (isCancelled()) throw CancellationException("Runtime package download cancelled.")
            val platform = componentDownloader.download(
                address = descriptor.downloadUrl,
                expectedSignature = descriptor.signature,
                publicKey = publicKey,
                destination = File(staging, descriptor.component),
                onProgress = { received, total ->
                    onProgress(descriptor.component, received, total)
                },
                isCancelled = isCancelled
            )

            val backup = if (destination.exists()) {
                File(parent, ".mojo-backup-${UUID.randomUUID()}").also {
                    check(destination.renameTo(it)) {
                        "Unable to stage the previous runtime package."
                    }
                }
            } else null
            try {
                check(staging.renameTo(destination)) {
                    "Unable to promote the authenticated runtime package."
                }
                backup?.deleteRecursively()
            } catch (failure: Exception) {
                if (backup != null && backup.exists() && !backup.renameTo(destination)) {
                    failure.addSuppressed(
                        IllegalStateException("The previous runtime package could not be restored.")
                    )
                }
                throw failure
            }
            return DownloadedMojoRuntimePackage(
                javaMajorVersion = descriptor.javaMajorVersion,
                architecture = architecture,
                directory = destination,
                universalComponent = VerifiedMojoRuntimeComponent(
                    File(destination, "universal.tar.xz"),
                    universal.sizeBytes,
                    universal.sha256
                ),
                platformComponent = VerifiedMojoRuntimeComponent(
                    File(destination, descriptor.component),
                    platform.sizeBytes,
                    platform.sha256
                )
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    companion object {
        private const val SIGNATURE_BYTES = 512
        private val SUPPORTED_JAVA_MAJORS = setOf(17, 21, 25)
    }
}

data class DownloadedMojoRuntimePackage(
    val javaMajorVersion: Int,
    val architecture: String,
    val directory: File,
    val universalComponent: VerifiedMojoRuntimeComponent,
    val platformComponent: VerifiedMojoRuntimeComponent
)
