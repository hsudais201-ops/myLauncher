package com.flame.launcher.content

import java.net.URI
import java.util.Locale

enum class MinecraftInstallAction {
    INSTALL_FILE,
    STAGE_MODPACK_ARCHIVE
}

data class MinecraftContentInstallItem(
    val project: MinecraftContentProject,
    val version: MinecraftContentVersion
)

data class MinecraftPlannedInstall(
    val provider: ContentProviderId,
    val projectId: String,
    val versionId: String,
    val contentType: MinecraftContentType,
    val action: MinecraftInstallAction,
    val fileName: String,
    val downloadUrl: String,
    val destinationPath: String,
    val expectedSizeBytes: Long?,
    val sha1: String?,
    val sha512: String?
)

data class MinecraftContentInstallPlan(
    val target: MinecraftInstanceTarget,
    val items: List<MinecraftPlannedInstall>
)

sealed interface MinecraftInstallPlanResult {
    data class Ready(val plan: MinecraftContentInstallPlan) : MinecraftInstallPlanResult
    data class Rejected(val reasons: List<String>) : MinecraftInstallPlanResult
}

/**
 * Creates a validated, side-effect-free install plan. This class never downloads,
 * creates directories, overwrites files, or changes a Minecraft instance.
 */
object MinecraftContentInstallPlanner {
    private const val MAX_FILE_SIZE_BYTES = 1L shl 30 // 1 GiB

    private val trustedDownloadHosts = mapOf(
        ContentProviderId.MODRINTH to setOf("cdn.modrinth.com"),
        // CurseForge file delivery hosts. Unknown hosts fail closed until explicitly reviewed.
        ContentProviderId.CURSEFORGE to setOf(
            "mediafilez.forgecdn.net",
            "edge.forgecdn.net"
        )
    )

    fun plan(
        items: List<MinecraftContentInstallItem>,
        target: MinecraftInstanceTarget,
        modpackStagingDirectory: String? = null
    ): MinecraftInstallPlanResult {
        val reasons = mutableListOf<String>()
        if (items.isEmpty()) {
            return MinecraftInstallPlanResult.Rejected(listOf("Select at least one content version"))
        }

        val contentRoot = safeAbsoluteDirectory(target.contentDirectory, "Instance content directory")
        val modpackRoot = modpackStagingDirectory?.let {
            safeAbsoluteDirectory(it, "Modpack staging directory")
        }
        val planned = mutableListOf<MinecraftPlannedInstall>()

        if (contentRoot == null) reasons += "Instance content directory must be a safe absolute path"
        if (items.any { it.project.contentType == MinecraftContentType.MODPACK } && modpackRoot == null) {
            reasons += "A safe app-private staging directory is required for modpack imports"
        }

        items.forEach { item ->
            val project = item.project
            val version = item.version

            if (project.provider != version.provider || project.projectId != version.projectId) {
                reasons += "Selected version does not belong to project ${project.projectId}"
                return@forEach
            }

            when (val compatibility = MinecraftContentCompatibility.check(
                project.contentType,
                version,
                target
            )) {
                ContentCompatibility.Compatible -> Unit
                is ContentCompatibility.Incompatible -> reasons += compatibility.reasons.map {
                    "${project.title}: $it"
                }
            }

            if (!isSafeFileName(version.fileName)) {
                reasons += "${project.title}: unsafe file name"
                return@forEach
            }
            if (!isTrustedDownloadUrl(version.downloadUrl, version.provider)) {
                reasons += "${project.title}: download URL is not trusted for ${version.provider}"
                return@forEach
            }
            if (version.fileSizeBytes != null &&
                (version.fileSizeBytes <= 0 || version.fileSizeBytes > MAX_FILE_SIZE_BYTES)
            ) {
                reasons += "${project.title}: file size is outside the supported 1 byte–1 GiB range"
                return@forEach
            }
            if (version.sha1 != null && !version.sha1.matches(Regex("^[a-fA-F0-9]{40}$"))) {
                reasons += "${project.title}: invalid SHA-1 checksum"
                return@forEach
            }
            if (version.sha512 != null && !version.sha512.matches(Regex("^[a-fA-F0-9]{128}$"))) {
                reasons += "${project.title}: invalid SHA-512 checksum"
                return@forEach
            }

            val action: MinecraftInstallAction
            val destinationDirectory: String
            when (project.contentType) {
                MinecraftContentType.MOD -> {
                    action = MinecraftInstallAction.INSTALL_FILE
                    destinationDirectory = contentRoot?.let { join(it, "mods") } ?: return@forEach
                }
                MinecraftContentType.RESOURCE_PACK -> {
                    action = MinecraftInstallAction.INSTALL_FILE
                    destinationDirectory = contentRoot?.let { join(it, "resourcepacks") } ?: return@forEach
                }
                MinecraftContentType.SHADER -> {
                    action = MinecraftInstallAction.INSTALL_FILE
                    destinationDirectory = contentRoot?.let { join(it, "shaderpacks") } ?: return@forEach
                }
                MinecraftContentType.DATAPACK -> {
                    val worldRoot = target.selectedWorldDirectory?.let {
                        safeAbsoluteDirectory(it, "Selected world directory")
                    }
                    if (worldRoot == null) {
                        reasons += "${project.title}: select a safe world directory for this datapack"
                        return@forEach
                    }
                    action = MinecraftInstallAction.INSTALL_FILE
                    destinationDirectory = join(worldRoot, "datapacks")
                }
                MinecraftContentType.MODPACK -> {
                    action = MinecraftInstallAction.STAGE_MODPACK_ARCHIVE
                    destinationDirectory = modpackRoot ?: return@forEach
                }
            }

            planned += MinecraftPlannedInstall(
                provider = version.provider,
                projectId = version.projectId,
                versionId = version.versionId,
                contentType = project.contentType,
                action = action,
                fileName = version.fileName,
                downloadUrl = version.downloadUrl,
                destinationPath = join(destinationDirectory, version.fileName),
                expectedSizeBytes = version.fileSizeBytes,
                sha1 = version.sha1?.lowercase(Locale.ROOT),
                sha512 = version.sha512?.lowercase(Locale.ROOT)
            )
        }

        val duplicatePaths = planned.groupBy { it.destinationPath.lowercase(Locale.ROOT) }
            .filterValues { it.size > 1 }
            .keys
        if (duplicatePaths.isNotEmpty()) {
            reasons += "Multiple selected items target the same destination file"
        }

        return if (reasons.isEmpty()) {
            MinecraftInstallPlanResult.Ready(MinecraftContentInstallPlan(target, planned))
        } else {
            MinecraftInstallPlanResult.Rejected(reasons.distinct())
        }
    }

    private fun isSafeFileName(fileName: String): Boolean {
        if (fileName.isBlank() || fileName != fileName.trim() || fileName == "." || fileName == "..") {
            return false
        }
        return fileName.none { it == '/' || it == '\\' || it == ':' || it.code < 32 || it.code == 127 }
    }

    private fun safeAbsoluteDirectory(path: String, label: String): String? {
        if (path.isBlank() || path != path.trim() || !path.startsWith("/") || path.startsWith("//")) {
            return null
        }
        if (path.any { it == '\\' || it.code < 32 || it.code == 127 }) return null
        val segments = path.split('/')
        if (segments.any { it == "." || it == ".." }) return null
        return path.trimEnd('/').ifEmpty {
            if (label.isNotEmpty()) "/" else null
        }
    }

    private fun isTrustedDownloadUrl(rawUrl: String, provider: ContentProviderId): Boolean {
        return try {
            val uri = URI(rawUrl)
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.fragment == null &&
                (uri.port == -1 || uri.port == 443) &&
                uri.host.lowercase(Locale.ROOT) in trustedDownloadHosts.getValue(provider)
        } catch (_: Exception) {
            false
        }
    }

    private fun join(directory: String, child: String): String =
        if (directory == "/") "/$child" else "$directory/$child"
}
