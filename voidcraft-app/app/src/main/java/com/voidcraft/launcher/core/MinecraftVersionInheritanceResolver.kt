package com.voidcraft.launcher.core

import java.io.IOException
import java.util.LinkedHashMap

/**
 * Narrow source contract so inheritance behavior can be tested without network
 * access. Production implementations should return entries from a trusted
 * official or otherwise separately-validated version catalog.
 */
interface MinecraftVersionMetadataSource {
    fun findEntry(versionId: String): MinecraftVersionEntry?
    fun fetchDetails(entry: MinecraftVersionEntry): MinecraftVersionDetails
}

class MojangMinecraftVersionMetadataSource(
    private val client: MinecraftVersionCatalogClient = MinecraftVersionCatalogClient()
) : MinecraftVersionMetadataSource {
    @Volatile
    private var cachedCatalog: MinecraftVersionCatalog? = null

    @Synchronized
    private fun catalog(): MinecraftVersionCatalog {
        val cached = cachedCatalog
        if (cached != null) return cached
        val loaded = client.fetchCatalog()
        cachedCatalog = loaded
        return loaded
    }

    override fun findEntry(versionId: String): MinecraftVersionEntry? = catalog().find(versionId)

    override fun fetchDetails(entry: MinecraftVersionEntry): MinecraftVersionDetails =
        client.fetchDetails(entry)
}

class VersionInheritanceException(message: String) : IOException(message)

/**
 * Merges the metadata subset currently modeled by VoidCraft. The child wins
 * for scalar values it explicitly defines. Libraries are keyed by full Maven
 * coordinate, so a child declaration replaces a parent declaration with the
 * same name while otherwise preserving parent-then-child order.
 *
 * Launch arguments, logging, downloads other than client/asset-index, and
 * loader-specific metadata are not yet modeled and therefore are not merged.
 */
object MinecraftVersionInheritanceMerger {
    fun merge(
        child: MinecraftVersionDetails,
        resolvedParent: MinecraftVersionDetails
    ): MinecraftVersionDetails {
        require(child.inheritsFrom == resolvedParent.id) {
            "Child " + child.id + " does not inherit from " + resolvedParent.id
        }
        require(resolvedParent.inheritsFrom == null) {
            "Parent version " + resolvedParent.id + " must be recursively resolved before merging"
        }

        val combinedLibraries = LinkedHashMap<String, MinecraftLibrary>()
        resolvedParent.libraries.forEach { combinedLibraries[it.name] = it }
        child.libraries.forEach { combinedLibraries[it.name] = it }

        val resolvedClient = child.clientDownload ?: resolvedParent.clientDownload
        val resolvedClientVersion = when {
            child.clientDownload != null -> child.clientDownloadVersionId ?: child.id
            resolvedParent.clientDownload != null ->
                resolvedParent.clientDownloadVersionId ?: resolvedParent.id
            else -> null
        }

        return child.copy(
            mainClass = child.mainClass ?: resolvedParent.mainClass,
            assets = child.assets ?: resolvedParent.assets,
            assetIndex = child.assetIndex ?: resolvedParent.assetIndex,
            clientDownload = resolvedClient,
            javaMajorVersion = child.javaMajorVersion ?: resolvedParent.javaMajorVersion,
            libraries = combinedLibraries.values.toList(),
            inheritsFrom = null,
            clientDownloadVersionId = resolvedClientVersion,
            arguments = mergeArguments(child.arguments, resolvedParent.arguments),
            legacyMinecraftArguments = child.legacyMinecraftArguments ?: resolvedParent.legacyMinecraftArguments
        )
    }

    private fun mergeArguments(
        child: MinecraftVersionArguments?,
        parent: MinecraftVersionArguments?
    ): MinecraftVersionArguments? {
        if (child == null) return parent
        if (parent == null) return child
        return MinecraftVersionArguments(
            game = child.game ?: parent.game,
            jvm = child.jvm ?: parent.jvm
        )
    }
}

/**
 * Resolves one version and all of its parents with cycle and depth protection.
 * Only metadata is resolved here: this does not download artifacts, extract
 * native archives, resolve loader-specific manifests, or launch the game.
 */
class MinecraftVersionInheritanceResolver(
    private val source: MinecraftVersionMetadataSource,
    private val maxDepth: Int = DEFAULT_MAX_DEPTH
) {
    init {
        require(maxDepth in 1..MAX_SUPPORTED_DEPTH) { "Inheritance depth limit is outside the supported range" }
    }

    fun resolve(entry: MinecraftVersionEntry): MinecraftVersionDetails =
        resolveInternal(entry, emptyList())

    private fun resolveInternal(
        entry: MinecraftVersionEntry,
        ancestors: List<String>
    ): MinecraftVersionDetails {
        if (entry.id in ancestors) {
            throw VersionInheritanceException("Version inheritance cycle: " + (ancestors + entry.id).joinToString(" -> "))
        }
        if (ancestors.size >= maxDepth) {
            throw VersionInheritanceException("Version inheritance exceeded the depth limit of " + maxDepth)
        }

        val details = source.fetchDetails(entry)
        if (details.id != entry.id) {
            throw VersionInheritanceException(
                "Metadata ID " + details.id + " does not match requested version " + entry.id
            )
        }

        val parentId = details.inheritsFrom ?: return details
        if (parentId == entry.id || parentId in ancestors) {
            throw VersionInheritanceException(
                "Version inheritance cycle: " + (ancestors + entry.id + parentId).joinToString(" -> ")
            )
        }

        val parentEntry = source.findEntry(parentId)
            ?: throw VersionInheritanceException("Inherited parent version was not found: " + parentId)
        if (parentEntry.id != parentId) {
            throw VersionInheritanceException(
                "Catalog returned " + parentEntry.id + " while resolving parent " + parentId
            )
        }
        val resolvedParent = resolveInternal(parentEntry, ancestors + entry.id)
        return try {
            MinecraftVersionInheritanceMerger.merge(details, resolvedParent)
        } catch (error: IllegalArgumentException) {
            throw VersionInheritanceException(
                "Could not merge inherited version " + entry.id + ": " + (error.message ?: "invalid parent metadata")
            )
        }
    }

    companion object {
        const val DEFAULT_MAX_DEPTH = 32
        const val MAX_SUPPORTED_DEPTH = 64
    }
}
