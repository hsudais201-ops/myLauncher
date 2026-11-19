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
    val nativeLibrariesPending: List<String>,
    val unresolvedLibraries: List<String>
) {
    val orderedClasspathPaths: List<String>
        get() = listOf(clientJarPath) + libraryJarPaths
}

internal object MinecraftLaunchClasspathPlanner {
    internal fun plan(
        metadataBytes: ByteArray,
        expectedVersionId: String,
        ruleContext: MojangLibraryRuleContext
    ): MinecraftLaunchClasspathPlan {
        val launchMetadata = MinecraftLaunchMetadataParser.parse(metadataBytes, expectedVersionId)
        val root = JSONObject(MojangMetadataText.decode(metadataBytes))
        val libraries = root.optJSONArray("libraries")
        val paths = linkedMapOf<String, Pair<Long, String>>()
        val nativeLibraries = linkedSetOf<String>()
        val unresolvedLibraries = linkedSetOf<String>()

        if (libraries != null) {
            require(libraries.length() <= MAX_LIBRARY_COUNT) {
                "Version metadata contains too many libraries."
            }
            for (index in 0 until libraries.length()) {
                val library = libraries.optJSONObject(index)
                    ?: error("Version metadata contains a malformed library entry.")
                if (!MojangLibraryRulePolicy.isAllowed(library, ruleContext)) continue

                val name = library.optString("name").takeIf { it.isNotBlank() }
                    ?: "library-$index"
                val downloads = if (library.has("downloads")) {
                    library.optJSONObject("downloads")
                        ?: error("Minecraft library '$name' has an invalid downloads object.")
                } else {
                    null
                }
                val artifact = if (downloads?.has("artifact") == true) {
                    downloads.optJSONObject("artifact")
                        ?: error("Minecraft library '$name' has an invalid artifact object.")
                } else {
                    null
                }
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

                if (library.has("natives")) {
                    val nativeMappings = library.optJSONObject("natives")
                        ?: error("Minecraft library '$name' has an invalid natives object.")
                    // Any declared native mapping remains a launch blocker until
                    // Flame can select and extract a verified Android-compatible
                    // payload. A Linux mapping is never treated as Android.
                    nativeLibraries += name
                    val keys = nativeMappings.keys()
                    while (keys.hasNext()) {
                        val osName = keys.next()
                        val classifierName = nativeMappings.optString(osName)
                        require(classifierName.isNotBlank()) {
                            "Minecraft library '$name' has an empty native classifier mapping."
                        }
                        if (osName == ruleContext.platformName) {
                            val classifiers = downloads?.optJSONObject("classifiers")
                            require(classifiers?.has(classifierName) == true) {
                                "Minecraft library '$name' is missing its declared native classifier."
                            }
                        }
                    }
                }
                if (artifact == null) {
                    unresolvedLibraries += name
                }
            }
        }

        validateDestinationPaths(paths.keys)
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
            nativeLibrariesPending = nativeLibraries.toList(),
            unresolvedLibraries = unresolvedLibraries.toList()
        )
    }

    /**
     * SAF providers may be case-insensitive and cannot represent a file as a
     * parent directory. Reject ambiguous destinations before any downloader writes.
     */
    private fun validateDestinationPaths(paths: Set<String>) {
        val canonicalFiles = linkedMapOf<String, String>()
        val canonicalDirectories = mutableSetOf<String>()
        for (path in paths) {
            val segments = LibraryArtifactPathPolicy.segments(path)
            val canonical = segments.map { it.lowercase(java.util.Locale.ROOT) }
            val canonicalPath = canonical.joinToString("/")
            val previous = canonicalFiles.putIfAbsent(canonicalPath, path)
            require(previous == null || previous == path) {
                "Version metadata declares case-conflicting library paths '$previous' and '$path'."
            }
            for (index in 1 until canonical.size) {
                val parent = canonical.take(index).joinToString("/")
                require(parent !in canonicalFiles) {
                    "Library artifact '$path' conflicts with file destination '${canonicalFiles[parent]}'."
                }
                canonicalDirectories += parent
            }
            require(canonicalPath !in canonicalDirectories) {
                "Library artifact '$path' conflicts with a directory destination."
            }
        }
    }
    private val SHA1_PATTERN = Regex("[a-f0-9]{40}")
    private const val MAX_LIBRARY_COUNT = 1000
    private const val MAX_ARTIFACT_BYTES = 512L * 1024L * 1024L
}
