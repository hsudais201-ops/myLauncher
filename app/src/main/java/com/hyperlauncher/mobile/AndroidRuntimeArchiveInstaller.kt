package com.hyperlauncher.mobile

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream

/**
 * Installs a pinned Android OpenJDK package into app-private storage.
 *
 * The archive is extracted into a fresh staging directory. The Android
 * provenance marker is written only after the release descriptor and
 * executable have been validated. The completed runtime is then published
 * with a same-filesystem move.
 */
object AndroidRuntimeInstaller {
    fun install(
        runtimePackage: AndroidRuntimePackage,
        cacheDirectory: File,
        runtimeRoot: File,
        onDownloadProgress: ((downloaded: Long, total: Long) -> Unit)? = null,
        onStage: ((stage: String) -> Unit)? = null,
        shouldCancel: (() -> Boolean)? = null
    ): AndroidRuntimeDescriptor {
        require(
            AndroidRuntimePackageCatalog.exact(runtimePackage.javaMajor, runtimePackage.abi) ==
                runtimePackage
        ) { "Android runtime package is not in the verified package catalog" }

        val root = runtimeRoot.canonicalFile
        if (!root.exists() && !root.mkdirs()) error("Could not create runtime directory")
        require(root.isDirectory) { "Runtime path is not a directory" }

        val runtimeName = "HyperRuntime-${runtimePackage.javaMajor}-${runtimePackage.abi}-" +
            runtimePackage.sha256.take(12)
        val target = File(root, runtimeName)
        if (target.exists()) {
            val installed = AndroidRuntimeInventory.inspectDirectory(target, runtimePackage.abi)
                ?: error("An incomplete runtime already exists at the selected install path")
            val marker = File(target, "hyperlauncher-runtime.properties")
            require(
                installed.javaMajor == runtimePackage.javaMajor &&
                    marker.readText().lineSequence().any {
                        it.trim() == "archiveSha256=${runtimePackage.sha256}"
                    }
            ) { "Existing runtime does not match the selected package" }
            return installed
        }

        MinecraftStoragePreflight.check(
            storageRoot = root,
            requiredDownloadBytes = runtimePackage.sizeBytes,
            additionalRequiredBytes = AndroidRuntimeArchiveExtractor.MAX_TOTAL_EXTRACTED_BYTES
        )
        val archive = VerifiedAndroidRuntimeDownloader.download(
            runtimePackage,
            cacheDirectory,
            onProgress = onDownloadProgress,
            shouldCancel = shouldCancel
        )
        val staging = File(root, ".install-${UUID.randomUUID()}")
        try {
            onStage?.invoke("extracting")
            val stagedRuntime = AndroidRuntimeArchiveExtractor.extract(
                archive = archive,
                destination = staging,
                expectedJavaMajor = runtimePackage.javaMajor,
                expectedAbi = runtimePackage.abi,
                archiveSha256 = runtimePackage.sha256,
                sourceUrl = runtimePackage.downloadUrl,
                shouldCancel = shouldCancel
            )
            if (shouldCancel?.invoke() == true) {
                throw CancellationException("Android runtime installation cancelled")
            }
            onStage?.invoke("publishing")
            try {
                Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(staging.toPath(), target.toPath())
            }
            return stagedRuntime.copy(name = runtimeName)
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        } finally {
            archive.delete()
        }
    }
}

/**
 * Bounded and path-safe TAR.XZ extraction for Android OpenJDK bundles.
 *
 * This utility does not run the extracted JVM. It verifies the package's
 * release descriptor and executable, then writes the launcher-owned marker
 * required by AndroidRuntimeInventory.
 */
object AndroidRuntimeArchiveExtractor {
    private const val MAX_ENTRY_BYTES = 256L * 1024L * 1024L
    const val MAX_TOTAL_EXTRACTED_BYTES = 512L * 1024L * 1024L
    private const val MAX_ENTRIES = 100_000
    private const val BUFFER_SIZE = 32 * 1024
    private const val XZ_MEMORY_LIMIT_KIB = 256 * 1024
    private val windowsDrivePath = Regex("^[A-Za-z]:")

    internal fun extract(
        archive: File,
        destination: File,
        expectedJavaMajor: Int,
        expectedAbi: String,
        archiveSha256: String,
        sourceUrl: String,
        shouldCancel: (() -> Boolean)? = null
    ): AndroidRuntimeDescriptor {
        require(expectedJavaMajor > 0) { "Expected Java major must be positive" }
        require(expectedAbi in listOf("arm64-v8a", "x86_64")) { "Unsupported Android runtime ABI" }
        require(Regex("^[a-fA-F0-9]{64}$").matches(archiveSha256)) {
            "Runtime archive SHA-256 is invalid"
        }
        require(archive.isFile) { "Runtime archive does not exist" }
        require(!destination.exists()) { "Runtime staging directory already exists" }
        if (!destination.mkdirs()) error("Could not create runtime staging directory")

        try {
            extractEntries(archive, destination, shouldCancel)

            val descriptor = AndroidRuntimeInventory.inspectDirectory(
                directory = destination,
                deviceAbi = expectedAbi,
                requireProvenanceMarker = false
            ) ?: throw IllegalArgumentException("Runtime archive is missing a valid Android Java runtime")
            require(descriptor.javaMajor == expectedJavaMajor) {
                "Runtime Java version did not match the selected package"
            }

            val marker = File(destination, "hyperlauncher-runtime.properties")
            marker.writeText(
                "platform=android\n" +
                    "abi=$expectedAbi\n" +
                    "javaMajor=$expectedJavaMajor\n" +
                    "archiveSha256=$archiveSha256\n" +
                    "source=$sourceUrl\n"
            )
            return AndroidRuntimeInventory.inspectDirectory(destination, expectedAbi)
                ?: error("Installed runtime failed final inventory validation")
        } catch (error: Exception) {
            destination.deleteRecursively()
            throw error
        }
    }

    private fun extractEntries(
        archive: File,
        destination: File,
        shouldCancel: (() -> Boolean)?
    ) {
        val root = destination.canonicalFile.toPath().normalize()
        var totalBytes = 0L
        var entryCount = 0

        XZInputStream(
            BufferedInputStream(FileInputStream(archive), BUFFER_SIZE),
            XZ_MEMORY_LIMIT_KIB
        ).use { xz ->
            TarArchiveInputStream(xz).use { tar ->
                while (true) {
                    checkCancellation(shouldCancel)
                    val entry = tar.getNextTarEntry() ?: break
                    entryCount++
                    require(entryCount <= MAX_ENTRIES) {
                        "Runtime archive contains too many entries"
                    }

                    val relativeName = normalizeEntryName(entry.name, entry.isDirectory)
                    if (relativeName == null) {
                        require(entry.isDirectory) { "Runtime archive contains an empty file path" }
                        continue
                    }
                    val target = root.resolve(relativeName).normalize()
                    require(target != root && target.startsWith(root)) {
                        "Runtime archive path escapes the staging directory"
                    }

                    when {
                        entry.isDirectory -> createDirectory(root, target)
                        entry.isSymbolicLink -> createSymbolicLink(root, target, entry.linkName)
                        entry.isLink -> createHardLink(root, target, entry.linkName)
                        entry.isFile -> {
                            val entrySize = entry.size
                            require(entrySize >= 0L && entrySize <= MAX_ENTRY_BYTES) {
                                "Runtime archive entry exceeds the extraction limit"
                            }
                            totalBytes = Math.addExact(totalBytes, entrySize)
                            require(totalBytes <= MAX_TOTAL_EXTRACTED_BYTES) {
                                "Runtime archive exceeds the total extraction limit"
                            }
                            extractRegularFile(root, target, entrySize, entry.mode, tar, shouldCancel)
                        }
                        else -> throw IllegalArgumentException(
                            "Runtime archive contains an unsupported special file"
                        )
                    }
                }
            }
        }
    }

    private fun normalizeEntryName(name: String, isDirectory: Boolean): String? {
        require(name.isNotBlank() && name.length <= 4096) {
            "Runtime archive contains an invalid path"
        }
        require(!name.startsWith("/") && !windowsDrivePath.containsMatchIn(name)) {
            "Runtime archive contains an absolute path"
        }
        require(name.none { it.code < 0x20 || it.code == 0x7f } && name.indexOf(92.toChar()) < 0) {
            "Runtime archive path contains invalid characters"
        }

        var normalized = name
        while (normalized.startsWith("./")) normalized = normalized.substring(2)
        if (isDirectory && normalized.endsWith("/")) normalized = normalized.removeSuffix("/")
        if (normalized.isEmpty() || normalized == ".") {
            return if (isDirectory) null else throw IllegalArgumentException(
                "Runtime archive contains an empty file path"
            )
        }

        val segments = normalized.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "Runtime archive contains an unsafe path segment"
        }
        return segments.joinToString("/")
    }

    private fun ensureContainedParentDirectory(root: Path, target: Path) {
        val parent = target.parent ?: error("Runtime archive entry has no parent")
        val canonicalParent = parent.toFile().canonicalFile.toPath().normalize()
        require(canonicalParent.startsWith(root)) {
            "Runtime archive entry follows a link outside the staging directory"
        }
        Files.createDirectories(parent)
        val createdParent = parent.toFile().canonicalFile.toPath().normalize()
        require(createdParent.startsWith(root) && Files.isDirectory(parent)) {
            "Runtime archive parent is not a safe directory"
        }
    }

    private fun createDirectory(root: Path, target: Path) {
        ensureContainedParentDirectory(root, target)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                "Runtime archive directory collides with another entry"
            }
            return
        }
        Files.createDirectory(target)
    }

    private fun createSymbolicLink(root: Path, target: Path, linkName: String) {
        require(linkName.isNotBlank() && linkName.length <= 4096) {
            "Runtime archive contains an invalid symbolic link"
        }
        require(!linkName.startsWith("/") && !windowsDrivePath.containsMatchIn(linkName) &&
            linkName.none { it.code < 0x20 || it.code == 0x7f } &&
            linkName.indexOf(92.toChar()) < 0
        ) { "Runtime archive symbolic link target is invalid" }

        val linkPath = Paths.get(linkName)
        require(!linkPath.isAbsolute) { "Runtime archive symbolic link must be relative" }
        val resolvedLink = target.parent.resolve(linkPath).normalize()
        require(resolvedLink.startsWith(root)) {
            "Runtime archive symbolic link escapes the staging directory"
        }
        ensureContainedParentDirectory(root, target)
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            "Runtime archive contains a duplicate output path"
        }
        Files.createSymbolicLink(target, linkPath)
    }

    private fun createHardLink(root: Path, target: Path, linkName: String) {
        val relativeTarget = normalizeEntryName(linkName, isDirectory = false)
            ?: error("Runtime archive hard link target is empty")
        val hardLinkTarget = root.resolve(relativeTarget).normalize()
        require(hardLinkTarget != root && hardLinkTarget.startsWith(root)) {
            "Runtime archive hard link escapes the staging directory"
        }
        ensureContainedParentDirectory(root, target)
        ensureContainedParentDirectory(root, hardLinkTarget)
        require(Files.isRegularFile(hardLinkTarget, LinkOption.NOFOLLOW_LINKS)) {
            "Runtime archive hard link target is missing or is not a regular file"
        }
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            "Runtime archive contains a duplicate output path"
        }
        Files.createLink(target, hardLinkTarget)
    }

    private fun extractRegularFile(
        root: Path,
        target: Path,
        expectedSize: Long,
        mode: Int,
        tar: TarArchiveInputStream,
        shouldCancel: (() -> Boolean)?
    ) {
        ensureContainedParentDirectory(root, target)
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            "Runtime archive contains a duplicate output path"
        }

        var written = 0L
        FileOutputStream(target.toFile()).use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (written < expectedSize) {
                checkCancellation(shouldCancel)
                val request = minOf(buffer.size.toLong(), expectedSize - written).toInt()
                val count = tar.read(buffer, 0, request)
                if (count < 0) throw EOFException("Runtime archive entry ended unexpectedly")
                if (count == 0) continue
                written = Math.addExact(written, count.toLong())
                output.write(buffer, 0, count)
            }
            output.fd.sync()
        }

        val executable = mode and 0b001001001 != 0
        if (executable && !target.toFile().setExecutable(true, false)) {
            throw IOException("Could not mark runtime executable")
        }
    }

    private fun checkCancellation(shouldCancel: (() -> Boolean)?) {
        if (shouldCancel?.invoke() == true) {
            throw CancellationException("Android runtime extraction cancelled")
        }
    }
}
