package com.flame.launcher.content

import java.io.File
import java.util.Locale

/**
 * Produces a side-effect-free destination plan for the contents of a validated .mrpack.
 * Required and optional client files are kept separate so a UI can ask before installing
 * optional files. Server-only/unsupported client entries are never scheduled.
 */
class MinecraftModpackManifestInstallPlanner {
    data class PlannedFile(
        val relativePath: String,
        val destination: File,
        val file: MinecraftModpackManifestParser.RemoteFile
    )

    data class Plan(
        val requiredFiles: List<PlannedFile>,
        val optionalFiles: List<PlannedFile>,
        val skippedClientUnsupported: Int
    )

    sealed interface Result {
        data class Ready(val plan: Plan) : Result
        data class Rejected(val reasons: List<String>) : Result
    }

    fun plan(
        manifest: MinecraftModpackManifestParser.Manifest,
        target: MinecraftInstanceTarget,
        overrideFiles: List<String> = emptyList()
    ): Result {
        val reasons = mutableListOf<String>()
        if (manifest.minecraftVersion != target.minecraftVersion) {
            reasons += "Modpack requires Minecraft ${manifest.minecraftVersion}, but the selected profile uses ${target.minecraftVersion}"
        }

        val requiredLoader = manifest.loaderDependencies.keys.singleOrNull()
        if (manifest.loaderDependencies.size > 1) {
            reasons += "Modpack declares more than one mod loader"
        } else if (requiredLoader != null && requiredLoader != target.loader.manifestKey()) {
            reasons += "Modpack loader does not match the selected profile"
        } else if (requiredLoader != null && target.loader == MinecraftLoader.UNKNOWN) {
            reasons += "Select a supported mod loader before importing this modpack"
        }

        val root = try {
            File(target.contentDirectory).canonicalFile
        } catch (_: Exception) {
            reasons += "Instance content directory is invalid"
            null
        }
        if (root != null && (!root.isAbsolute || target.contentDirectory.isBlank())) {
            reasons += "Instance content directory must be an absolute path"
        }

        val overrideTargets = mutableListOf<String>()
        for (entry in overrideFiles) {
            val relative = entry.removePrefix("overrides/")
            if (!entry.startsWith("overrides/") || !isSafeRelativePath(relative)) {
                reasons += "Archive contains an unsafe client override path"
                continue
            }
            overrideTargets += relative
        }

        val candidates = manifest.files.filter { it.clientEnvironment != "unsupported" }
        val remotePaths = candidates.map { it.path }
        val allPaths = (remotePaths + overrideTargets).map { it.lowercase(Locale.ROOT) }
        if (allPaths.distinct().size != allPaths.size) {
            reasons += "Modpack manifest files conflict with each other or with client overrides"
        }
        if (hasFileParentConflict(remotePaths + overrideTargets)) {
            reasons += "Modpack contains conflicting file and directory paths"
        }

        if (reasons.isNotEmpty() || root == null) {
            return Result.Rejected(reasons.distinct())
        }

        fun planned(file: MinecraftModpackManifestParser.RemoteFile): PlannedFile {
            val destination = File(root, file.path).canonicalFile
            return PlannedFile(file.path, destination, file)
        }

        val required = candidates.filter { it.clientEnvironment == "required" }.map(::planned)
        val optional = candidates.filter { it.clientEnvironment == "optional" }.map(::planned)
        return Result.Ready(
            Plan(
                requiredFiles = required,
                optionalFiles = optional,
                skippedClientUnsupported = manifest.files.size - candidates.size
            )
        )
    }

    private fun hasFileParentConflict(paths: List<String>): Boolean {
        val files = paths.toSet()
        return paths.any { path ->
            val segments = path.split('/')
            segments.dropLast(1).indices.any { index ->
                segments.take(index + 1).joinToString("/") in files
            }
        }
    }

    private fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.length > 1024 || path.startsWith('/') ||
            path.contains('\\') || path.contains('\u0000') ||
            (path.length >= 2 && path[1] == ':')
        ) return false
        return path.split('/').none { it.isBlank() || it == "." || it == ".." }
    }

    private fun MinecraftLoader.manifestKey(): String? = when (this) {
        MinecraftLoader.FABRIC -> "fabric-loader"
        MinecraftLoader.FORGE -> "forge"
        MinecraftLoader.NEOFORGE -> "neoforge"
        MinecraftLoader.QUILT -> "quilt-loader"
        MinecraftLoader.UNKNOWN -> null
    }
}
