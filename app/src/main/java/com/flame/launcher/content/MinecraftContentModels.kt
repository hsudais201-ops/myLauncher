package com.flame.launcher.content

/**
 * Provider-neutral content types used by the Minecraft Content browser.
 * These describe downloadable Minecraft projects, not Flame Feature Store add-ons.
 */
enum class MinecraftContentType {
    MOD,
    MODPACK,
    RESOURCE_PACK,
    SHADER,
    DATAPACK
}

enum class MinecraftLoader {
    FABRIC,
    FORGE,
    NEOFORGE,
    QUILT,
    UNKNOWN
}

enum class ContentProviderId {
    MODRINTH,
    CURSEFORGE
}

enum class DependencyType {
    REQUIRED,
    OPTIONAL,
    INCOMPATIBLE,
    EMBEDDED
}

data class MinecraftContentProject(
    val provider: ContentProviderId,
    val projectId: String,
    val slug: String,
    val title: String,
    val description: String,
    val contentType: MinecraftContentType,
    val author: String,
    val downloads: Long = 0,
    val iconUrl: String? = null
)

data class MinecraftContentVersion(
    val provider: ContentProviderId,
    val projectId: String,
    val versionId: String,
    val versionNumber: String,
    val displayName: String,
    val gameVersions: Set<String>,
    val loaders: Set<MinecraftLoader>,
    val fileName: String,
    val downloadUrl: String,
    val fileSizeBytes: Long? = null,
    val sha1: String? = null,
    val sha512: String? = null,
    val dependencies: List<MinecraftContentDependency> = emptyList()
)

data class MinecraftContentDependency(
    val projectId: String,
    val versionId: String? = null,
    val type: DependencyType
)

/**
 * The selected instance is the source of truth for compatibility filtering.
 * Datapack installation additionally requires a selected world.
 */
data class MinecraftInstanceTarget(
    val minecraftVersion: String,
    val loader: MinecraftLoader,
    val contentDirectory: String,
    val selectedWorldDirectory: String? = null
)

sealed interface ContentCompatibility {
    data object Compatible : ContentCompatibility
    data class Incompatible(val reasons: List<String>) : ContentCompatibility
}

object MinecraftContentCompatibility {
    fun check(
        contentType: MinecraftContentType,
        version: MinecraftContentVersion,
        target: MinecraftInstanceTarget
    ): ContentCompatibility {
        val reasons = buildList {
            if (target.minecraftVersion !in version.gameVersions) {
                add("Not available for Minecraft ${target.minecraftVersion}")
            }
            if (version.loaders.isNotEmpty() &&
                target.loader != MinecraftLoader.UNKNOWN &&
                target.loader !in version.loaders
            ) {
                add("Not available for loader ${target.loader}")
            }
            if (contentType == MinecraftContentType.DATAPACK &&
                target.selectedWorldDirectory.isNullOrBlank()
            ) {
                add("Select a world before installing a datapack")
            }
        }
        return if (reasons.isEmpty()) ContentCompatibility.Compatible
        else ContentCompatibility.Incompatible(reasons)
    }
}
