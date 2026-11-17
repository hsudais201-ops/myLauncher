package com.mflegend.flamelauncher.catalog

import org.json.JSONArray
import org.json.JSONObject

/**
 * The version-specific data required to prepare a real Java launch.
 *
 * This parses Mojang metadata only. It does not evaluate OS rules, build the
 * final JVM command, load a Java runtime, or claim that the game can launch.
 */
data class MinecraftLaunchMetadata(
    val versionId: String,
    val mainClass: String,
    val requiredJavaMajorVersion: Int?,
    val javaRuntimeComponent: String?,
    val hasModernGameArguments: Boolean,
    val hasLegacyGameArguments: Boolean
)

object MinecraftLaunchMetadataParser {
    fun parse(metadataBytes: ByteArray, expectedVersionId: String): MinecraftLaunchMetadata {
        require(expectedVersionId.matches(Regex("[A-Za-z0-9._-]{1,100}"))) {
            "Invalid version identifier."
        }
        val root = JSONObject(metadataBytes.toString(Charsets.UTF_8))
        val versionId = root.optString("id")
        require(versionId == expectedVersionId) {
            "Launch metadata version ID does not match the selected version."
        }

        val mainClass = root.optString("mainClass")
        require(MAIN_CLASS_PATTERN.matches(mainClass)) {
            "Version metadata does not contain a valid Minecraft main class."
        }

        val javaMajorVersion: Int?
        val javaRuntimeComponent: String?
        if (root.has("javaVersion")) {
            val javaVersion = root.optJSONObject("javaVersion")
                ?: error("Version metadata contains an invalid javaVersion object.")
            val major = javaVersion.optInt("majorVersion", -1)
            require(major in MIN_SUPPORTED_JAVA_MAJOR..MAX_SUPPORTED_JAVA_MAJOR) {
                "Version metadata contains an invalid required Java major version."
            }
            val component = javaVersion.optString("component")
            require(component.matches(RUNTIME_COMPONENT_PATTERN)) {
                "Version metadata contains an invalid Java runtime component."
            }
            javaMajorVersion = major
            javaRuntimeComponent = component
        } else {
            javaMajorVersion = null
            javaRuntimeComponent = null
        }

        val arguments = if (root.has("arguments")) {
            root.optJSONObject("arguments")
                ?: error("Version metadata contains an invalid arguments object.")
        } else {
            null
        }
        arguments?.let {
            validateArgumentArray(it, "game")
            validateArgumentArray(it, "jvm")
        }

        val hasModernGameArguments = arguments?.has("game") == true
        val legacyArguments = root.optString("minecraftArguments").takeIf { it.isNotBlank() }
        require(hasModernGameArguments || legacyArguments != null) {
            "Version metadata does not contain game launch arguments."
        }

        return MinecraftLaunchMetadata(
            versionId = versionId,
            mainClass = mainClass,
            requiredJavaMajorVersion = javaMajorVersion,
            javaRuntimeComponent = javaRuntimeComponent,
            hasModernGameArguments = hasModernGameArguments,
            hasLegacyGameArguments = legacyArguments != null
        )
    }

    private fun validateArgumentArray(arguments: JSONObject, key: String) {
        if (!arguments.has(key)) return
        val values = arguments.optJSONArray(key)
            ?: error("Version metadata launch arguments '$key' are not an array.")
        for (index in 0 until values.length()) {
            when (val item = values.get(index)) {
                is String -> Unit
                is JSONObject -> {
                    val argumentValue = item.opt("value")
                    when (argumentValue) {
                        is String -> Unit
                        is JSONArray -> {
                            for (valueIndex in 0 until argumentValue.length()) {
                                require(argumentValue.get(valueIndex) is String) {
                                    "Version metadata contains a non-string launch argument value."
                                }
                            }
                        }
                        else -> error("Version metadata contains a malformed conditional launch argument.")
                    }
                    if (item.has("rules")) {
                        val rules = item.optJSONArray("rules")
                            ?: error("Version metadata launch argument rules are not an array.")
                        for (ruleIndex in 0 until rules.length()) {
                            require(rules.get(ruleIndex) is JSONObject) {
                                "Version metadata contains a malformed launch argument rule."
                            }
                        }
                    }
                }
                else -> error("Version metadata contains an unsupported launch argument entry.")
            }
        }
    }

    private const val MIN_SUPPORTED_JAVA_MAJOR = 8
    private const val MAX_SUPPORTED_JAVA_MAJOR = 99
    private val MAIN_CLASS_PATTERN = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")
    private val RUNTIME_COMPONENT_PATTERN = Regex("[A-Za-z0-9._-]{1,80}")
}
