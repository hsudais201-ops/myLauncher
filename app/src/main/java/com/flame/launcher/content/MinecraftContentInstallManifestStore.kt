package com.flame.launcher.content

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.Locale
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A durable ownership record for content files installed by Flame.
 *
 * Construct this with an app-private directory (normally a child of Context.filesDir).
 * The manifest never grants permission to delete a file by itself: callers must also
 * verify the recorded SHA-256 against the current file before a future uninstall.
 */
data class MinecraftManagedContentInstall(
    val provider: ContentProviderId,
    val projectId: String,
    val versionId: String,
    val contentType: MinecraftContentType,
    val fileName: String,
    val installedPath: String,
    val sha256: String,
    val expectedSha1: String? = null,
    val expectedSha512: String? = null,
    val expectedSizeBytes: Long? = null,
    val selectedWorldDirectory: String? = null
)

data class MinecraftContentInstallManifest(
    val instanceContentDirectory: String,
    val entries: List<MinecraftManagedContentInstall>,
    val schemaVersion: Int = 1
)

sealed interface MinecraftManagedContentUninstallResult {
    data class Uninstalled(val entry: MinecraftManagedContentInstall) : MinecraftManagedContentUninstallResult
    data class Failed(val reason: String) : MinecraftManagedContentUninstallResult
}

sealed interface MinecraftContentInstallManifestResult {
    data class Loaded(
        val manifest: MinecraftContentInstallManifest,
        val existed: Boolean
    ) : MinecraftContentInstallManifestResult

    data class Saved(
        val manifest: MinecraftContentInstallManifest,
        val manifestFile: File
    ) : MinecraftContentInstallManifestResult

    data class Failed(val reason: String) : MinecraftContentInstallManifestResult
}

/**
 * Stores one versioned manifest per Minecraft content directory in app-private storage.
 * Writes use a synced temporary file and same-directory replace to avoid half-written JSON
 * or properties data surviving a process interruption.
 */
class MinecraftContentInstallManifestStore(
    private val appPrivateDirectory: File
) {
    @Synchronized
    fun load(instanceContentDirectory: File): MinecraftContentInstallManifestResult {
        val instanceRoot = canonicalInstanceRoot(instanceContentDirectory)
            ?: return failed("Invalid instance content directory")
        val file = manifestFile(instanceRoot)
            ?: return failed("App-private manifest directory is unavailable")
        if (!file.exists()) {
            return MinecraftContentInstallManifestResult.Loaded(
                MinecraftContentInstallManifest(instanceRoot.path, emptyList()),
                existed = false
            )
        }
        if (!file.isFile || !file.canRead()) return failed("Install manifest is not a readable file")

        return try {
            val properties = Properties()
            file.inputStream().use(properties::load)
            val schema = properties.getProperty("schema.version")?.toIntOrNull()
                ?: return failed("Install manifest has no valid schema version")
            if (schema != SCHEMA_VERSION) return failed("Unsupported install manifest schema version")
            val storedRoot = properties.getProperty("instance.content.directory")
                ?: return failed("Install manifest is missing its instance directory")
            if (File(storedRoot).canonicalFile != instanceRoot) {
                return failed("Install manifest belongs to a different instance directory")
            }
            val count = properties.getProperty("entry.count")?.toIntOrNull()
                ?: return failed("Install manifest has no valid entry count")
            if (count !in 0..MAX_ENTRIES) return failed("Install manifest entry count is outside the supported range")

            val entries = ArrayList<MinecraftManagedContentInstall>(count)
            for (index in 0 until count) {
                val prefix = "entry.$index."
                val entry = parseEntry(properties, prefix)
                    ?: return failed("Install manifest entry $index is incomplete or invalid")
                if (!isValidManagedPath(instanceRoot, entry)) {
                    return failed("Install manifest entry $index points outside its approved content directory")
                }
                entries += entry
            }
            if (entries.map { it.installedPath }.distinct().size != entries.size) {
                return failed("Install manifest contains duplicate paths")
            }
            MinecraftContentInstallManifestResult.Loaded(
                MinecraftContentInstallManifest(instanceRoot.path, entries, schema),
                existed = true
            )
        } catch (_: Exception) {
            failed("Install manifest could not be read")
        }
    }

    /**
     * Records only files reported as installed by the transaction and still present with
     * the expected size/checksums. Modpack archives are deliberately excluded: they remain
     * in the separate staging/import flow and are not instance content files.
     */
    @Synchronized
    fun recordSuccessfulInstall(
        plan: MinecraftContentInstallPlan,
        installed: MinecraftContentInstallTransactionResult.Installed,
        additionalEntries: List<MinecraftManagedContentInstall> = emptyList()
    ): MinecraftContentInstallManifestResult {
        val instanceRoot = canonicalInstanceRoot(File(plan.target.contentDirectory))
            ?: return failed("Invalid instance content directory")
        if (plan.items.isEmpty() || installed.installedPaths.size != plan.items.size + additionalEntries.size) {
            return failed("Transaction result does not match the install plan")
        }

        val expectedPaths = mutableListOf<String>()
        val newEntries = mutableListOf<MinecraftManagedContentInstall>()
        for (item in plan.items) {
            val destination = try {
                File(item.destinationPath).canonicalFile
            } catch (_: Exception) {
                return failed("Invalid installed destination")
            }
            expectedPaths += destination.path
            if (item.action == MinecraftInstallAction.STAGE_MODPACK_ARCHIVE) continue

            val worldDirectory = if (item.contentType == MinecraftContentType.DATAPACK) {
                plan.target.selectedWorldDirectory?.let {
                    try { File(it).canonicalFile.path } catch (_: Exception) { null }
                } ?: return failed("Datapack install has no valid selected world")
            } else null

            val entry = createEntry(item, destination, worldDirectory)
                ?: return failed("Installed file verification failed for ${item.projectId}")
            if (!isValidManagedPath(instanceRoot, entry)) {
                return failed("Installed destination is outside its approved content directory")
            }
            newEntries += entry
        }

        for (entry in additionalEntries) {
            if (!isValidManagedPath(instanceRoot, entry)) {
                return failed("Additional managed file is outside the selected instance directory")
            }
            val file = File(entry.installedPath)
            if (!file.isFile || !file.canRead() || file.length() != entry.expectedSizeBytes ||
                sha256(file) != entry.sha256
            ) return failed("Additional managed file verification failed")
        }

        val actualPaths = installed.installedPaths.map { path ->
            try { File(path).canonicalFile.path } catch (_: Exception) { return failed("Invalid transaction result path") }
        }
        if (!expectedPaths.all { it in actualPaths } ||
            actualPaths.distinct().size != actualPaths.size ||
            additionalEntries.map { File(it.installedPath).canonicalPath }.toSet().size != additionalEntries.size ||
            additionalEntries.any { File(it.installedPath).canonicalPath !in actualPaths }
        ) {
            return failed("Transaction result paths do not match the install plan")
        }

        val current = when (val loaded = load(instanceRoot)) {
            is MinecraftContentInstallManifestResult.Loaded -> loaded.manifest.entries
            is MinecraftContentInstallManifestResult.Failed -> return loaded
            is MinecraftContentInstallManifestResult.Saved -> return failed("Unexpected manifest load result")
        }
        val allNewEntries = newEntries + additionalEntries
        val merged = (current.filterNot { existing ->
            allNewEntries.any { it.installedPath == existing.installedPath }
        } + allNewEntries).sortedBy { it.installedPath }
        val manifest = MinecraftContentInstallManifest(instanceRoot.path, merged)
        return save(instanceRoot, manifest)
    }

    /**
     * Returns true only while the file still matches the digest captured after installation.
     * Future uninstall/update flows must call this before touching a managed file.
     */
    fun verifyManagedFile(entry: MinecraftManagedContentInstall): Boolean {
        return try {
            val file = File(entry.installedPath)
            file.isFile && file.canonicalFile.path == entry.installedPath &&
                isSafeLeaf(entry.fileName) && file.name == entry.fileName &&
                sha256(file) == entry.sha256.lowercase(Locale.ROOT)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Removes a managed file without a check/delete race: first move the candidate to a
     * unique quarantine name in the same directory, then verify the moved bytes again.
     * Only after the second verification is the manifest updated and quarantine deleted.
     */
    @Synchronized
    fun uninstallManagedFile(
        instanceContentDirectory: File,
        installedPath: String
    ): MinecraftManagedContentUninstallResult {
        val instanceRoot = canonicalInstanceRoot(instanceContentDirectory)
            ?: return MinecraftManagedContentUninstallResult.Failed("Invalid instance content directory")
        val manifest = when (val loaded = load(instanceRoot)) {
            is MinecraftContentInstallManifestResult.Loaded -> loaded.manifest
            is MinecraftContentInstallManifestResult.Failed ->
                return MinecraftManagedContentUninstallResult.Failed(loaded.reason)
            is MinecraftContentInstallManifestResult.Saved ->
                return MinecraftManagedContentUninstallResult.Failed("Unexpected manifest load result")
        }
        val canonicalPath = try {
            File(installedPath).canonicalPath
        } catch (_: Exception) {
            return MinecraftManagedContentUninstallResult.Failed("Invalid installed file path")
        }
        val entry = manifest.entries.singleOrNull { it.installedPath == canonicalPath }
            ?: return MinecraftManagedContentUninstallResult.Failed("File is not recorded as Flame-managed")
        if (!verifyManagedFile(entry)) {
            return MinecraftManagedContentUninstallResult.Failed(
                "Managed file is missing or has changed; it was not deleted"
            )
        }

        val original = File(entry.installedPath)
        val quarantine = File(
            original.parentFile,
            ".flame-uninstall-${java.util.UUID.randomUUID()}.tmp"
        )
        val moved = try {
            try {
                Files.move(original.toPath(), quarantine.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(original.toPath(), quarantine.toPath())
            }
            true
        } catch (_: Exception) {
            false
        }
        if (!moved) {
            return MinecraftManagedContentUninstallResult.Failed("Managed file could not be safely isolated")
        }

        val quarantineMatches = try {
            quarantine.isFile && quarantine.length() > 0L &&
                sha256(quarantine) == entry.sha256.lowercase(Locale.ROOT)
        } catch (_: Exception) {
            false
        }
        if (!quarantineMatches) {
            val restored = restoreQuarantinedFile(quarantine, original)
            return MinecraftManagedContentUninstallResult.Failed(
                if (restored) "Managed file changed during uninstall; original file was restored"
                else "Managed file changed during uninstall; recovery file remains at ${quarantine.name}"
            )
        }

        val updated = manifest.copy(entries = manifest.entries.filterNot { it.installedPath == entry.installedPath })
        when (val saved = save(instanceRoot, updated)) {
            is MinecraftContentInstallManifestResult.Saved -> Unit
            is MinecraftContentInstallManifestResult.Failed -> {
                val restored = restoreQuarantinedFile(quarantine, original)
                return MinecraftManagedContentUninstallResult.Failed(
                    if (restored) saved.reason
                    else "${saved.reason}; recovery file remains at ${quarantine.name}"
                )
            }
            is MinecraftContentInstallManifestResult.Loaded -> {
                val restored = restoreQuarantinedFile(quarantine, original)
                return MinecraftManagedContentUninstallResult.Failed(
                    if (restored) "Unexpected manifest save result"
                    else "Manifest save failed; recovery file remains at ${quarantine.name}"
                )
            }
        }

        val deleted = try {
            quarantine.delete()
        } catch (_: Exception) {
            false
        }
        if (!deleted && quarantine.exists()) {
            val restored = restoreQuarantinedFile(quarantine, original)
            val ownershipRestored = when (save(instanceRoot, manifest)) {
                is MinecraftContentInstallManifestResult.Saved -> true
                else -> false
            }
            return MinecraftManagedContentUninstallResult.Failed(
                when {
                    restored && ownershipRestored -> "Managed file could not be deleted; original file and ownership record were restored"
                    !restored -> "Managed file could not be deleted; recovery file remains at ${quarantine.name}"
                    else -> "Managed file could not be deleted and ownership record restoration failed"
                }
            )
        }
        return MinecraftManagedContentUninstallResult.Uninstalled(entry)
    }

    private fun restoreQuarantinedFile(quarantine: File, original: File): Boolean {
        if (!quarantine.exists() || original.exists()) return false
        return try {
            try {
                Files.move(quarantine.toPath(), original.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(quarantine.toPath(), original.toPath())
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun save(
        instanceRoot: File,
        manifest: MinecraftContentInstallManifest
    ): MinecraftContentInstallManifestResult {
        val file = manifestFile(instanceRoot)
            ?: return failed("App-private manifest directory is unavailable")
        val temporary = try {
            File.createTempFile(".flame-content-manifest-", ".tmp", file.parentFile)
        } catch (_: Exception) {
            return failed("Could not create temporary install manifest")
        }
        return try {
            val properties = Properties()
            properties.setProperty("schema.version", SCHEMA_VERSION.toString())
            properties.setProperty("instance.content.directory", instanceRoot.path)
            properties.setProperty("entry.count", manifest.entries.size.toString())
            manifest.entries.forEachIndexed { index, entry ->
                val prefix = "entry.$index."
                properties.setProperty(prefix + "provider", entry.provider.name)
                properties.setProperty(prefix + "project.id", entry.projectId)
                properties.setProperty(prefix + "version.id", entry.versionId)
                properties.setProperty(prefix + "content.type", entry.contentType.name)
                properties.setProperty(prefix + "file.name", entry.fileName)
                properties.setProperty(prefix + "installed.path", entry.installedPath)
                properties.setProperty(prefix + "sha256", entry.sha256)
                entry.expectedSha1?.let { properties.setProperty(prefix + "expected.sha1", it) }
                entry.expectedSha512?.let { properties.setProperty(prefix + "expected.sha512", it) }
                entry.expectedSizeBytes?.let { properties.setProperty(prefix + "expected.size", it.toString()) }
                entry.selectedWorldDirectory?.let { properties.setProperty(prefix + "world.directory", it) }
            }
            FileOutputStream(temporary).use { output ->
                properties.store(output, "Flame Launcher managed Minecraft content")
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            MinecraftContentInstallManifestResult.Saved(manifest, file)
        } catch (_: Exception) {
            failed("Install manifest could not be saved")
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun createEntry(
        item: MinecraftPlannedInstall,
        destination: File,
        worldDirectory: String?
    ): MinecraftManagedContentInstall? {
        return try {
            if (!destination.isFile || !destination.canRead()) return null
            val size = destination.length()
            if (size <= 0L || (item.expectedSizeBytes != null && size != item.expectedSizeBytes)) return null
            val digest = sha256(destination)
            if (item.sha1 != null && digestFile(destination, "SHA-1") != item.sha1.lowercase(Locale.ROOT)) return null
            if (item.sha512 != null && digestFile(destination, "SHA-512") != item.sha512.lowercase(Locale.ROOT)) return null
            MinecraftManagedContentInstall(
                provider = item.provider,
                projectId = item.projectId,
                versionId = item.versionId,
                contentType = item.contentType,
                fileName = item.fileName,
                installedPath = destination.path,
                sha256 = digest,
                expectedSha1 = item.sha1,
                expectedSha512 = item.sha512,
                expectedSizeBytes = item.expectedSizeBytes,
                selectedWorldDirectory = worldDirectory
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseEntry(
        properties: Properties,
        prefix: String
    ): MinecraftManagedContentInstall? {
        return try {
            val provider = ContentProviderId.valueOf(properties.getProperty(prefix + "provider") ?: return null)
            val contentType = MinecraftContentType.valueOf(properties.getProperty(prefix + "content.type") ?: return null)
            val projectId = properties.getProperty(prefix + "project.id") ?: return null
            val versionId = properties.getProperty(prefix + "version.id") ?: return null
            val fileName = properties.getProperty(prefix + "file.name") ?: return null
            val installedPath = properties.getProperty(prefix + "installed.path") ?: return null
            val sha256 = properties.getProperty(prefix + "sha256") ?: return null
            val sha1 = properties.getProperty(prefix + "expected.sha1")
            val sha512 = properties.getProperty(prefix + "expected.sha512")
            val sizeValue = properties.getProperty(prefix + "expected.size")
            val size = sizeValue?.toLongOrNull()
            val world = properties.getProperty(prefix + "world.directory")
            if (!isSafeIdentifier(projectId) || !isSafeIdentifier(versionId) ||
                !isSafeLeaf(fileName) || !isSha256(sha256) ||
                (sha1 != null && !sha1.matches(Regex("^[a-fA-F0-9]{40}$"))) ||
                (sha512 != null && !sha512.matches(Regex("^[a-fA-F0-9]{128}$"))) ||
                (sizeValue != null && (size == null || size <= 0L))
            ) return null
            MinecraftManagedContentInstall(
                provider, projectId, versionId, contentType, fileName, installedPath,
                sha256.lowercase(Locale.ROOT), sha1?.lowercase(Locale.ROOT),
                sha512?.lowercase(Locale.ROOT), size, world
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun isValidManagedPath(
        instanceRoot: File,
        entry: MinecraftManagedContentInstall
    ): Boolean {
        if (entry.contentType == MinecraftContentType.MODPACK || !isSafeLeaf(entry.fileName)) return false
        val allowedRoot = when (entry.contentType) {
            MinecraftContentType.MOD -> File(instanceRoot, "mods")
            MinecraftContentType.RESOURCE_PACK -> File(instanceRoot, "resourcepacks")
            MinecraftContentType.SHADER -> File(instanceRoot, "shaderpacks")
            MinecraftContentType.DATAPACK -> {
                val world = entry.selectedWorldDirectory ?: return false
                File(File(world).canonicalFile, "datapacks")
            }
            MinecraftContentType.MODPACK -> instanceRoot
        }.canonicalFile
        val installed = File(entry.installedPath)
        val canonicalInstalled = installed.canonicalFile
        return installed.isAbsolute && canonicalInstalled.path == entry.installedPath &&
            installed.name == entry.fileName &&
            isWithin(allowedRoot, canonicalInstalled) &&
            isSha256(entry.sha256)
    }

    private fun manifestFile(instanceRoot: File): File? {
        return try {
            if (!appPrivateDirectory.exists() && !appPrivateDirectory.mkdirs()) return null
            val privateRoot = appPrivateDirectory.canonicalFile
            if (!privateRoot.isDirectory || !privateRoot.canWrite()) return null
            File(privateRoot, "minecraft-content-${sha256(instanceRoot.path.toByteArray())}.properties")
        } catch (_: Exception) {
            null
        }
    }

    private fun canonicalInstanceRoot(directory: File): File? = try {
        directory.canonicalFile.takeIf { it.isAbsolute && it.path != File.separator }
    } catch (_: Exception) {
        null
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().toHex()
    }

    private fun digestFile(file: File, algorithm: String): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance(algorithm)
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().toHex()
    }

    private fun sha256(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(value).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun isWithin(root: File, candidate: File): Boolean {
        val rootPath = root.toPath().normalize()
        val candidatePath = candidate.toPath().normalize()
        return candidatePath != rootPath && candidatePath.startsWith(rootPath)
    }

    private fun isSafeLeaf(value: String): Boolean =
        value.isNotBlank() && value == value.trim() && value != "." && value != ".." &&
            value.none { it == '/' || it == '\\' || it == ':' || it.code < 32 || it.code == 127 }

    private fun isSafeIdentifier(value: String): Boolean =
        value.isNotBlank() && value.length <= 512 &&
            value.none { it.code < 32 || it.code == 127 }

    private fun isSha256(value: String): Boolean = value.matches(Regex("^[a-fA-F0-9]{64}$"))

    private fun failed(reason: String) = MinecraftContentInstallManifestResult.Failed(reason)

    private companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_ENTRIES = 10_000
    }
}
