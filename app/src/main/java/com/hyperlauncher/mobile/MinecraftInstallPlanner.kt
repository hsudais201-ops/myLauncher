package com.hyperlauncher.mobile

data class MinecraftInstallPlan(
    val versionId: String,
    val clientJar: PlannedMinecraftFile,
    val assetIndexFile: PlannedMinecraftFile,
    val assetIndex: MinecraftAssetIndex,
    val libraries: List<PlannedMinecraftFile>,
    val assets: List<PlannedMinecraftFile>,
    val totalAssetBytes: Long
)

data class PlannedMinecraftFile(
    val descriptor: MinecraftDownload,
    val relativePath: String,
    val kind: String
)

/**
 * Builds a download plan for the client JAR and all indexed assets.
 * Library/native resolution and execution are separate future stages.
 */
object MinecraftInstallPlanner {
    fun create(version: MinecraftVersion): MinecraftInstallPlan {
        val details = MinecraftVersionDetailsResolver.fetch(version)
        val indexDescriptor = details.assetIndex
            ?: throw IllegalStateException("Selected Minecraft version has no asset index")
        val indexId = details.assetIndexId
            ?: throw IllegalStateException("Selected Minecraft version has no asset index ID")
        val assetIndex = MinecraftAssetIndexResolver.fetch(indexDescriptor, indexId)
        val indexFile = PlannedMinecraftFile(
            descriptor = indexDescriptor,
            relativePath = "assets/indexes/$indexId.json",
            kind = "asset-index"
        )

        val client = PlannedMinecraftFile(
            descriptor = details.client,
            relativePath = "versions/${version.id}/${version.id}.jar",
            kind = "client"
        )
        val assets = assetIndex.objects.map { asset ->
            PlannedMinecraftFile(
                descriptor = asset.download,
                relativePath = asset.relativePath,
                kind = "asset"
            )
        }
        return MinecraftInstallPlan(
            versionId = version.id,
            clientJar = client,
            assetIndexFile = indexFile,
            assetIndex = assetIndex,
            libraries = details.libraries.map { it.artifact },
            assets = assets,
            totalAssetBytes = assetIndex.totalBytes
        )
    }
}
