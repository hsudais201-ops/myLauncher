package com.mflegend.flamelauncher.catalog

import android.net.Uri
import android.os.Build
import com.mflegend.flamelauncher.storage.SafGameStorage

data class MojangVersionInstallResult(
    val versionId: String,
    val launchMetadata: MinecraftLaunchMetadata,
    val classpathPlan: MinecraftLaunchClasspathPlan,
    val clientJarBytes: Long,
    val libraries: LibraryDownloadResult,
    val assets: AssetDownloadResult
)

/**
 * Coordinates the currently supported vanilla payload stages.
 * This is not a game launcher: native runtime files, rule evaluation and launch are separate work.
 */
class MojangVersionInstaller(
    private val manifestClient: MojangVersionManifestClient = MojangVersionManifestClient(),
    private val clientJarDownloader: MojangClientJarDownloader = MojangClientJarDownloader(),
    private val libraryDownloader: MojangLibraryDownloader = MojangLibraryDownloader(),
    private val assetDownloader: MojangAssetDownloader = MojangAssetDownloader()
) {
    fun install(
        version: MojangVersion,
        storage: SafGameStorage,
        control: DownloadControl = DownloadControl()
    ): MojangVersionInstallResult {
        require(version.id.matches(Regex("[A-Za-z0-9._-]{1,100}"))) {
            "Invalid version identifier."
        }
        control.checkpoint()
        control.progress("Version metadata", 0, null)
        val metadataBytes = manifestClient.fetchVersionMetadata(version, control)
        val launchMetadata = MinecraftLaunchMetadataParser.parse(metadataBytes, version.id)
        control.checkpoint()
        control.progress("Version metadata", 1, 1)
        val root = storage.root()
        val versionsDirectory = findOrCreateDirectory(storage, root, "versions")
        val versionDirectory = findOrCreateDirectory(storage, versionsDirectory, version.id)
        saveMetadata(storage, versionDirectory, version.id + ".json", metadataBytes)
        control.progress("Version metadata", 1, 1)

        val clientJar = clientJarDownloader.download(
            version.id,
            metadataBytes,
            storage,
            versionDirectory,
            control
        )
        val libraries = libraryDownloader.download(metadataBytes, storage, root, control)
        val assets = assetDownloader.download(metadataBytes, storage, root, control)
        val classpathPlan = MinecraftLaunchClasspathPlanner.plan(
            metadataBytes,
            version.id,
            MojangLibraryRuleContext(
                platformName = "android",
                osVersion = Build.VERSION.RELEASE.orEmpty(),
                osArchitecture = System.getProperty("os.arch")
                    ?: Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            )
        )

        return MojangVersionInstallResult(
            versionId = version.id,
            launchMetadata = launchMetadata,
            classpathPlan = classpathPlan,
            clientJarBytes = clientJar.byteCount,
            libraries = libraries,
            assets = assets
        )
    }

    private fun saveMetadata(storage: SafGameStorage, directory: Uri, name: String, bytes: ByteArray) {
        val existing = storage.findFile(directory, name)
        val temporaryName = ".$name.download"
        val uri = storage.createTemporaryFile(directory, temporaryName, "application/json")
        try {
            storage.openOutput(uri).use { output ->
                output.write(bytes)
                output.flush()
            }
            storage.replaceWithVerifiedFile(uri, existing?.uri, name)
        } catch (error: Exception) {
            storage.deleteTemporaryAfterFailure(uri, error)
            throw error
        }
    }

    private fun findOrCreateDirectory(storage: SafGameStorage, parent: Uri, name: String): Uri =
        storage.findDirectory(parent, name)?.uri ?: storage.createDirectory(parent, name)
}
