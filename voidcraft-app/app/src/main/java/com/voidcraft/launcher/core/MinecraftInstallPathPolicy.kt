package com.voidcraft.launcher.core

import java.io.File
import java.io.IOException

/** Shared path and URL policy for install-plan execution and integrity scans. */
internal object MinecraftInstallPathPolicy {
    fun validatePlan(plan: MinecraftInstallPlan): List<String> {
        require(plan.downloads.isNotEmpty()) { "Install plan contains no downloads" }
        MinecraftVersionManifestParser().validateVersionId(plan.versionId)
        val paths = plan.downloads.map { validateArtifactPath(it, plan.versionId) }
        plan.downloads.forEach { artifact ->
            MojangUrlPolicy.validate(artifact.url)
            require(artifact.expectedSha1 == null || SHA1_PATTERN.matches(artifact.expectedSha1)) {
                "Invalid SHA-1 for planned artifact " + artifact.logicalName
            }
            require(artifact.expectedBytes == null || artifact.expectedBytes >= 0L) {
                "Negative expected size for planned artifact " + artifact.logicalName
            }
        }
        require(paths.distinct().size == paths.size) { "Install plan has duplicate destination paths" }
        return paths
    }

    fun validateArtifactPath(artifact: MinecraftPlannedDownload, versionId: String): String {
        val checked = MinecraftMavenPath.validateRelativePath(artifact.destinationRelativePath)
        require(!checked.substringBefore('/').startsWith(".voidcraft-")) {
            "Install path uses a reserved VoidCraft transaction name"
        }
        val allowedPrefix = when (artifact.kind) {
            MinecraftInstallPlanner.KIND_VERSION_METADATA,
            MinecraftInstallPlanner.KIND_CLIENT -> "versions/" + versionId + "/"
            MinecraftInstallPlanner.KIND_ASSET_INDEX -> "assets/indexes/"
            MinecraftInstallPlanner.KIND_ASSET_OBJECT -> "assets/objects/"
            MinecraftInstallPlanner.KIND_LIBRARY,
            MinecraftInstallPlanner.KIND_NATIVE_LIBRARY -> "libraries/"
            else -> throw IllegalArgumentException("Unknown install artifact kind: " + artifact.kind)
        }
        require(checked.startsWith(allowedPrefix) && checked.length > allowedPrefix.length) {
            "Artifact kind " + artifact.kind + " cannot write to " + checked
        }
        return checked
    }

    fun resolveInside(root: File, relativePath: String, allowReserved: Boolean = false): File {
        val checked = MinecraftMavenPath.validateRelativePath(relativePath)
        if (!allowReserved && checked.substringBefore('/').startsWith(".voidcraft-")) {
            throw IOException("Install path uses a reserved VoidCraft transaction name")
        }
        val canonicalRoot = root.canonicalFile
        val target = File(canonicalRoot, checked).canonicalFile
        val rootPrefix = canonicalRoot.path.trimEnd(File.separatorChar) + File.separator
        if (!target.path.startsWith(rootPrefix)) {
            throw IOException("Resolved path escapes its root: " + relativePath)
        }
        return target
    }

    private val SHA1_PATTERN = Regex("[0-9a-fA-F]{40}")
}
