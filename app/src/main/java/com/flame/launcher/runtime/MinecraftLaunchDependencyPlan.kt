package com.flame.launcher.runtime

/**
 * A deterministic, validated download/classpath plan derived from Mojang metadata.
 *
 * This is planning only: it does not download, extract, or execute anything.
 * Native artifacts remain separate from the Java classpath because they must be
 * extracted and validated by a platform-specific runtime layer.
 */
object MinecraftLaunchDependencyPlanBuilder {
    fun build(
        details: MinecraftVersionDetails,
        environment: MinecraftLibraryEnvironment
    ): MinecraftLaunchDependencyPlan {
        val resolvedLibraries = MinecraftLibraryResolver.resolve(details.libraries, environment)
        val downloads = LinkedHashMap<String, MinecraftPlannedDownload>()
        val classPath = ArrayList<String>()
        val nativeLibraries = ArrayList<MinecraftPlannedDownload>()

        fun addDownload(download: MinecraftPlannedDownload) {
            val existing = downloads[download.relativePath]
            require(existing == null || existing.descriptor.sha1 == download.descriptor.sha1) {
                "Two different files resolve to the same installation path: ${download.relativePath}"
            }
            if (existing == null) downloads[download.relativePath] = download
        }

        val clientPath = "versions/${details.id}/${details.id}.jar"
        addDownload(
            MinecraftPlannedDownload(
                kind = MinecraftPlannedDownloadKind.CLIENT,
                relativePath = clientPath,
                descriptor = details.client
            )
        )
        classPath += clientPath

        val assetIndexPath = "assets/indexes/${details.assets}.json"
        addDownload(
            MinecraftPlannedDownload(
                kind = MinecraftPlannedDownloadKind.ASSET_INDEX,
                relativePath = assetIndexPath,
                descriptor = details.assetIndex
            )
        )

        resolvedLibraries.forEach { library ->
            library.artifact?.let { artifact ->
                val path = requireNotNull(artifact.path) {
                    "Library ${library.coordinate} has no validated artifact installation path."
                }
                val download = MinecraftPlannedDownload(
                    kind = MinecraftPlannedDownloadKind.LIBRARY,
                    relativePath = "libraries/$path",
                    descriptor = artifact
                )
                addDownload(download)
                classPath += download.relativePath
            }
            library.nativeArtifact?.let { native ->
                val path = requireNotNull(native.path) {
                    "Native library ${library.coordinate} has no validated installation path."
                }
                val download = MinecraftPlannedDownload(
                    kind = MinecraftPlannedDownloadKind.NATIVE,
                    relativePath = "libraries/$path",
                    descriptor = native,
                    extractExcludes = library.extractExcludes
                )
                addDownload(download)
                nativeLibraries += download
            }
        }

        return MinecraftLaunchDependencyPlan(
            versionId = details.id,
            mainClass = details.mainClass,
            requiredJavaMajorVersion = details.javaMajorVersion,
            assetIndexId = details.assets,
            downloads = downloads.values.toList(),
            classPath = classPath.distinct(),
            nativeLibraries = nativeLibraries.distinctBy { it.relativePath }
        )
    }
}

data class MinecraftLaunchDependencyPlan(
    val versionId: String,
    val mainClass: String,
    val requiredJavaMajorVersion: Int?,
    val assetIndexId: String,
    val downloads: List<MinecraftPlannedDownload>,
    val classPath: List<String>,
    val nativeLibraries: List<MinecraftPlannedDownload>
)

data class MinecraftPlannedDownload(
    val kind: MinecraftPlannedDownloadKind,
    val relativePath: String,
    val descriptor: MinecraftDownloadDescriptor,
    val extractExcludes: List<String> = emptyList()
)

enum class MinecraftPlannedDownloadKind {
    CLIENT,
    ASSET_INDEX,
    LIBRARY,
    NATIVE
}
