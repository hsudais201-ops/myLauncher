package com.flame.launcher.runtime

import java.io.File

/**
 * Downloads the plan's verified artifacts and then extracts selected native
 * archives into a version-scoped directory. Asset objects remain a separate
 * stage because they are driven by the parsed asset-index JSON.
 */
class MinecraftNativeInstallCoordinator(
    private val artifactInstaller: MinecraftArtifactInstallCoordinator = MinecraftArtifactInstallCoordinator(),
    private val extractor: MinecraftNativeArchiveExtractor = MinecraftNativeArchiveExtractor(),
    private val extractionValidator: MinecraftNativeExtractionValidator = MinecraftNativeExtractionValidator()
) {
    fun install(
        plan: MinecraftLaunchDependencyPlan,
        gameDirectory: File,
        nativeDirectory: File,
        onProgress: (MinecraftArtifactInstallProgress) -> Unit = {}
    ): MinecraftNativeInstallResult {
        val installedArtifacts = artifactInstaller.install(plan, gameDirectory, onProgress)
        val nativeArchives = plan.nativeLibraries.map { planned ->
            require(planned.kind == MinecraftPlannedDownloadKind.NATIVE) {
                "Minecraft native plan contains a non-native artifact."
            }
            val archive = File(gameDirectory, planned.relativePath)
            require(archive.isFile && archive.length() == planned.descriptor.size) {
                "Verified Minecraft native archive is missing."
            }
            MinecraftNativeArchiveExtractor.MinecraftNativeArchive(
                file = archive,
                excludes = planned.extractExcludes
            )
        }
        val extracted = if (nativeArchives.isEmpty()) {
            emptyList()
        } else {
            val files = extractor.extract(nativeArchives, nativeDirectory)
            val validation = extractionValidator.validate(nativeDirectory, files)
            check(validation is MinecraftNativeExtractionValidation.Complete) {
                "Minecraft native extraction did not pass completeness validation: $validation"
            }
            files
        }
        return MinecraftNativeInstallResult(installedArtifacts, extracted)
    }
}

data class MinecraftNativeInstallResult(
    val installedArtifacts: List<File>,
    val extractedNativeFiles: List<File>
)
