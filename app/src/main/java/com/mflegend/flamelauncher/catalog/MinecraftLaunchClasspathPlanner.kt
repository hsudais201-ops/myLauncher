package com.mflegend.flamelauncher.catalog

import org.json.JSONObject

/**
 * Builds the ordered, relative classpath file list for the vanilla client.
 *
 * Paths are logical paths beneath the selected Minecraft game root, never raw
 * filesystem paths. Native classifier extraction and JVM startup remain separate.
 */
data class MinecraftLaunchClasspathPlan(
    val versionId: String,
    val mainClass: String,
    val requiredJavaMajorVersion: Int?,
    val clientJarPath: String,
    val libraryJarPaths: List<String>,
    val nativeLibrariesPending: List<String>
) {
    val orderedClasspathPaths: List<String>
        get() = listOf(clientJarPath) + libraryJarPaths
}

internal object MinecraftLaunchClasspathPlanner {
    fun plan(
        metadataBytes: ByteArray,
        expectedVersionId: String,
        ruleContext: MojangLibraryRuleContext
    ): MinecraftLaunchClasspathPlan {
        val launchMetadata = MinecraftLaunchMetadataParser.parse(metadataBytes, expectedVersionId)
        val root = JSONObject(metadataBytes.toString(Charsets.UTF_8))
        val libraries = root.optJSONArray("libraries")
        val paths = linkedMapOf<String, Pair<Long, String>>()
        val nativeLibraries = linkedSetOf<String>()

        if (libraries != null) {
            require(libraries.length() <= MAX_LIBRARY_COUNT) {
                "Version metadata contains too many libraries."
            }
            for (index in 0 until libraries.length()) {
                val library = libraries.optJSONObject(index)
                    ?: error("Version metadata contains a malformed library entry.")
                if (!MojangLibraryRulePolicy.isAllowed(library, ruleContext)) continue

                val downloads = library.optJSONObject("downloads")
                val artifact = downloads?.optJSONObject("artifact")
                if (artifact != null) {
                    val relativePath = artifact.optString("path")
                    LibraryArtifactPathPolicy.segments(relativePath)
                    val sha1 = artifact.optString("sha1").lowercase()
                    require(sha1.matches(SHA1_PATTERN)) {
                        "Library artifact has no valid SHA-1 checksum: $relativePath"
                    }
                    val size = artifact.optLong("size", -1L)
                    require(size in 1..MAX_ARTIFACT_BYTES) {
                        "Library artifact has an invalid declared size: $relativePath"
                    }
                    val previous = paths.putIfAbsent(relativePath, size to sha1)
                    require(previous == null || previous == (size to sha1)) {
                        "Version metadata declares conflicting artifacts at '$relativePath'."
                    }
                }

                if (library.has("natives") || downloads?.has("classifiers") == true) {
                    val name = library.optString("name").takeIf { it.isNotBlank() }
                        ?: "library-$index"
                    nativeLibraries += name
                }
            }
        }

        val libraryPaths = paths.keys.toList()
        require(libraryPaths.size <= MAX_LIBRARY_COUNT) {
            "Version metadata contains too many classpath libraries."
        }

        return MinecraftLaunchClasspathPlan(
            versionId = expectedVersionId,
            mainClass = launchMetadata.mainClass,
            requiredJavaMajorVersion = launchMetadata.requiredJavaMajorVersion,
            clientJarPath = "versions/$expectedVersionId/$expectedVersionId.jar",
            libraryJarPaths = libraryPaths.map { "libraries/$it" },
            nativeLibrariesPending = nativeLibraries.toList()
        )
    }

    private val SHA1_PATTERN = Regex("[a-f0-9]{40}")
    private const val MAX_LIBRARY_COUNT = 1000
    private const val MAX_ARTIFACT_BYTES = 512L * 1024L * 1024L
}
