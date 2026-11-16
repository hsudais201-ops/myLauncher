package com.mflegend.flamelauncher.versions

import android.net.Uri
import com.mflegend.flamelauncher.storage.SafGameStorage
import org.json.JSONObject

data class InstalledMinecraftVersion(
    val id: String,
    val type: String?,
    val releaseTime: String?,
    val mainClass: String?,
    val inheritsFrom: String?,
    val assetIndexId: String?,
    val clientDownloadUrl: String?,
    val metadataUri: Uri
)

data class MinecraftVersionScanResult(
    val versionsDirectoryFound: Boolean,
    val versions: List<InstalledMinecraftVersion>,
    val foldersWithoutMetadata: List<String>,
    val warnings: List<String>
)

/** Reads local versions/<id>/<id>.json metadata through SAF. */
class MinecraftVersionScanner(private val storage: SafGameStorage) {
    fun scan(): MinecraftVersionScanResult {
        val rootEntries = storage.listChildren()
        val versionsDirectory = rootEntries.firstOrNull {
            it.isDirectory && it.name.equals("versions", ignoreCase = true)
        } ?: return MinecraftVersionScanResult(false, emptyList(), emptyList(), emptyList())

        val versionFolders = storage.listChildren(versionsDirectory.uri)
            .filter { it.isDirectory }
        val installed = mutableListOf<InstalledMinecraftVersion>()
        val missing = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        versionFolders.forEach { folder ->
            val metadata = storage.listChildren(folder.uri).firstOrNull {
                !it.isDirectory && it.name.equals(folder.name + ".json", ignoreCase = true)
            }
            if (metadata == null) {
                missing += folder.name
            } else {
                try {
                    val jsonText = storage.openInput(metadata.uri).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    require(jsonText.length <= MAX_METADATA_CHARS) { "Version metadata is unexpectedly large." }
                    val json = JSONObject(jsonText)
                    val assetIndex = json.optJSONObject("assetIndex")
                    val clientDownload = json.optJSONObject("downloads")?.optJSONObject("client")
                    installed += InstalledMinecraftVersion(
                        id = json.optString("id").takeIf { it.isNotBlank() } ?: folder.name,
                        type = json.optString("type").takeIf { it.isNotBlank() },
                        releaseTime = json.optString("releaseTime").takeIf { it.isNotBlank() },
                        mainClass = json.optString("mainClass").takeIf { it.isNotBlank() },
                        inheritsFrom = json.optString("inheritsFrom").takeIf { it.isNotBlank() },
                        assetIndexId = assetIndex?.optString("id")?.takeIf { it.isNotBlank() },
                        clientDownloadUrl = clientDownload?.optString("url")?.takeIf { it.isNotBlank() },
                        metadataUri = metadata.uri
                    )
                } catch (error: Exception) {
                    warnings += folder.name + ": " + (error.localizedMessage ?: "Invalid version metadata")
                }
            }
        }
        return MinecraftVersionScanResult(true, installed.sortedByDescending { it.releaseTime.orEmpty() }, missing, warnings)
    }

    private companion object {
        const val MAX_METADATA_CHARS = 2_000_000
    }
}
