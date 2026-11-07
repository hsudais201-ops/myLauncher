package com.flame.launcher.runtime

/**
 * Resolves Mojang library rules for one target runtime environment.
 *
 * Rules are evaluated in declaration order. A library with no rules is allowed;
 * a library with rules starts disallowed, and each matching rule overwrites the
 * previous decision. Native classifiers use Mojang's ${arch} 32/64 placeholder.
 */
object MinecraftLibraryResolver {
    fun resolve(
        libraries: List<MinecraftLibraryArtifact>,
        environment: MinecraftLibraryEnvironment
    ): List<MinecraftResolvedLibrary> = libraries.mapNotNull { library ->
        if (!isAllowed(library.rules, environment)) return@mapNotNull null

        val nativeTemplate = library.natives[environment.osName]
        val nativeArtifact = nativeTemplate?.let { template ->
            val archToken = 36.toChar().toString() + "{arch}"
            val classifier = if (template.contains(archToken)) {
                val nativeArch = requireNotNull(environment.nativeArchitecture) {
                    "Native library ${library.coordinate} requires a 32-bit or 64-bit architecture value."
                }
                template.replace(archToken, nativeArch)
            } else {
                template
            }
            library.classifiers[classifier]
                ?: throw IllegalArgumentException(
                    "Native library ${library.coordinate} is missing classifier $classifier."
                )
        }

        if (library.artifact == null && nativeArtifact == null) {
            null
        } else {
            MinecraftResolvedLibrary(
                coordinate = library.coordinate,
                artifact = library.artifact,
                nativeArtifact = nativeArtifact,
                extractExcludes = library.extractExcludes
            )
        }
    }

    private fun isAllowed(
        rules: List<MinecraftLibraryRule>,
        environment: MinecraftLibraryEnvironment
    ): Boolean {
        if (rules.isEmpty()) return true
        var allowed = false
        rules.forEach { rule ->
            if (matches(rule, environment)) {
                allowed = rule.action == "allow"
            }
        }
        return allowed
    }

    private fun matches(
        rule: MinecraftLibraryRule,
        environment: MinecraftLibraryEnvironment
    ): Boolean {
        if (rule.osName != null && rule.osName != environment.osName) return false
        if (rule.osArchPattern != null &&
            !Regex(rule.osArchPattern).matches(environment.osArchitecture)
        ) return false
        if (rule.osVersionPattern != null) {
            val version = environment.osVersion ?: return false
            if (!Regex(rule.osVersionPattern).matches(version)) return false
        }
        return rule.features.all { (name, required) ->
            (environment.features[name] ?: false) == required
        }
    }
}

data class MinecraftLibraryEnvironment(
    val osName: String,
    val osArchitecture: String,
    val osVersion: String? = null,
    val nativeArchitecture: String? = null,
    val features: Map<String, Boolean> = emptyMap()
) {
    init {
        require(osName in SUPPORTED_OS_NAMES) { "Minecraft target operating system is unsupported." }
        require(osArchitecture.isNotBlank() && osArchitecture.length <= 128) {
            "Minecraft target architecture is invalid."
        }
        require(nativeArchitecture == null || nativeArchitecture == "32" || nativeArchitecture == "64") {
            "Minecraft native architecture must be 32 or 64."
        }
        require(features.keys.all { FEATURE_NAME.matches(it) }) {
            "Minecraft target feature name is invalid."
        }
    }

    companion object {
        private val SUPPORTED_OS_NAMES = setOf("windows", "osx", "linux")
        private val FEATURE_NAME = Regex("[A-Za-z0-9_]{1,64}")
    }
}

data class MinecraftResolvedLibrary(
    val coordinate: String,
    val artifact: MinecraftDownloadDescriptor?,
    val nativeArtifact: MinecraftDownloadDescriptor?,
    val extractExcludes: List<String>
)
