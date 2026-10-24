package com.voidcraft.launcher.core

/**
 * Result of resolving and validating the metadata required to build a download
 * plan. This is not proof that Java/runtime integration is available and does
 * not launch Minecraft.
 */
data class MinecraftInstallPreparation(
    val requestedEntry: MinecraftVersionEntry,
    val resolvedDetails: MinecraftVersionDetails,
    val assetIndexContents: MinecraftAssetIndexContents?,
    val plan: MinecraftInstallPlan
) {
    val hasBlockingMetadataWarnings: Boolean
        get() = plan.warnings.isNotEmpty()
}

/**
 * Orchestrates the metadata-only preparation stage:
 * 1. recursively resolve the version's modeled inheritance fields,
 * 2. fetch and verify the inherited/effective asset index when present,
 * 3. produce a deterministic install download plan.
 *
 * Call from a worker thread; fetches may perform network I/O. No game artifact
 * is downloaded here. Native extraction and runtime launch are separate stages.
 */
class MinecraftVersionInstallPreparationService(
    private val source: MinecraftVersionMetadataSource,
    private val metadataClient: MinecraftVersionCatalogClient,
    private val planner: MinecraftInstallPlanner
) : MinecraftInstallPreparer {
    constructor(
        platform: MinecraftPlatformContext,
        client: MinecraftVersionCatalogClient = MinecraftVersionCatalogClient()
    ) : this(
        source = MojangMinecraftVersionMetadataSource(client),
        metadataClient = client,
        planner = MinecraftInstallPlanner(platform)
    )

    private val inheritanceResolver = MinecraftVersionInheritanceResolver(source)

    override fun prepare(entry: MinecraftVersionEntry): MinecraftInstallPreparation {
        val resolved = inheritanceResolver.resolve(entry)
        val assetContents = resolved.assetIndex?.let { metadataClient.fetchAssetIndex(it) }
        val plan = planner.createPlan(entry, resolved, assetContents)
        return MinecraftInstallPreparation(
            requestedEntry = entry,
            resolvedDetails = resolved,
            assetIndexContents = assetContents,
            plan = plan
        )
    }
}
