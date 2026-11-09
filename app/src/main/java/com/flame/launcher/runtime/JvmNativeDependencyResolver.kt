package com.flame.launcher.runtime

import java.io.File
import java.util.ArrayDeque

/**
 * ELF dynamic-section facts supplied by a platform-specific reader.
 *
 * The reader is intentionally injected: this resolver never shells out to
 * readelf, never loads a library, and never trusts DT_RPATH/DT_RUNPATH as
 * arbitrary filesystem search paths.
 */
/**
 * Parsed ELF dynamic strings. RPATH/RUNPATH values are informational only;
 * callers must not use them as filesystem search paths without a separate,
 * strict runtime-home policy.
 */
data class JvmElfDynamicMetadata(
    val neededLibraries: List<String>,
    val rpathEntries: List<String> = emptyList(),
    val runpathEntries: List<String> = emptyList()
)

fun interface JvmElfDynamicMetadataReader {
    fun inspect(elfFile: File): JvmElfDynamicMetadata
}

sealed interface JvmNativeDependencyResolution {
    data class Resolved(
        val rootLibrary: File,
        val runtimeLibraries: Map<String, File>,
        val approvedSystemLibraries: Set<String>,
        /** Runtime dependencies ordered deepest-first for safe explicit loading. */
        val runtimeLoadOrder: List<File>
    ) : JvmNativeDependencyResolution

    data class Unresolved(
        val library: String,
        val requestedBy: File
    ) : JvmNativeDependencyResolution

    data class Rejected(val diagnostic: String) : JvmNativeDependencyResolution
}

/**
 * Resolves DT_NEEDED names against explicitly approved runtime directories and
 * an exact-name Android system-library allowlist. It does not call dlopen.
 *
 * Every runtime dependency must resolve to one canonical file beneath the
 * selected runtime home. Ambiguous duplicate SONAMEs and path-like dependency
 * names fail closed rather than depending on linker search-order accidents.
 */
class JvmNativeDependencyResolver(
    private val metadataReader: JvmElfDynamicMetadataReader = JvmElfDynamicMetadataReaderImpl(),
    approvedSystemLibraries: Set<String> = DEFAULT_ANDROID_SYSTEM_LIBRARIES
) {
    private val approvedSystemLibraries = approvedSystemLibraries.toSet()

    fun resolve(
        rootLibrary: File,
        runtimeHome: File,
        searchDirectories: List<File>
    ): JvmNativeDependencyResolution {
        val home = runtimeHome.canonicalFile
        val root = rootLibrary.canonicalFile
        if (!home.isDirectory || !isInside(home, root) || !root.isFile) {
            return JvmNativeDependencyResolution.Rejected(
                "Root JVM library must be a file inside the selected runtime home."
            )
        }

        val directories = try {
            searchDirectories.map { it.canonicalFile }.distinct().also { dirs ->
                if (dirs.any { !it.isDirectory || !isInside(home, it) }) {
                    throw IllegalArgumentException(
                        "Native dependency search directories must exist inside the runtime home."
                    )
                }
            }
        } catch (error: Exception) {
            return JvmNativeDependencyResolution.Rejected(
                error.message ?: "Native dependency search directory is invalid."
            )
        }

        val resolved = linkedMapOf<String, File>()
        val system = linkedSetOf<String>()
        val visited = linkedSetOf<File>()
        val ordered = linkedSetOf<File>()
        var failure: JvmNativeDependencyResolution? = null

        // Visit each dependency before its requester. Android's linker may not
        // discover private JRE directories by SONAME, so the native bridge
        // explicitly retains these absolute paths before loading libjvm.
        fun visit(requester: File) {
            if (failure != null || !visited.add(requester)) return
            val needed = try {
                metadataReader.inspect(requester).neededLibraries
            } catch (error: Exception) {
                failure = JvmNativeDependencyResolution.Rejected(
                    "Unable to inspect ELF dependencies for ${requester.name}: " +
                        (error.message ?: "metadata reader failed.")
                )
                return
            }

            for (name in needed) {
                if (!isSafeSoname(name)) {
                    failure = JvmNativeDependencyResolution.Rejected(
                        "ELF dependency contains an invalid library name: $name"
                    )
                    return
                }
                if (name in approvedSystemLibraries) {
                    system += name
                    continue
                }

                val candidates = directories
                    .map { File(it, name) }
                    .filter { it.isFile }
                    .map { it.canonicalFile }
                    .filter { isInside(home, it) }
                    .distinct()

                if (candidates.isEmpty()) {
                    failure = JvmNativeDependencyResolution.Unresolved(name, requester)
                    return
                }
                if (candidates.size > 1) {
                    failure = JvmNativeDependencyResolution.Rejected(
                        "ELF dependency $name resolves to multiple runtime files."
                    )
                    return
                }

                val dependency = candidates.single()
                val previous = resolved[name]
                if (previous != null && previous != dependency) {
                    failure = JvmNativeDependencyResolution.Rejected(
                        "ELF dependency $name resolves inconsistently within the runtime."
                    )
                    return
                }
                resolved[name] = dependency
                visit(dependency)
                if (failure != null) return
                if (dependency != root) ordered += dependency
            }
        }

        visit(root)
        failure?.let { return it }
        return JvmNativeDependencyResolution.Resolved(
            rootLibrary = root,
            runtimeLibraries = resolved.toMap(),
            approvedSystemLibraries = system.toSet(),
            runtimeLoadOrder = ordered.toList()
        )
    }

    private fun isSafeSoname(name: String): Boolean =
        name.isNotBlank() &&
            name != "." &&
            name != ".." &&
            '/' !in name &&
            '\\' !in name &&
            '\u0000' !in name

    private fun isInside(home: File, file: File): Boolean =
        file.path.startsWith(home.path + File.separator)

    companion object {
        /**
         * Baseline public Android platform libraries. Keep this list narrow;
         * expand only after validating API-level and linker behavior per ABI.
         */
        val DEFAULT_ANDROID_SYSTEM_LIBRARIES = setOf(
            "libandroid.so",
            "libc.so",
            "libdl.so",
            "libEGL.so",
            "libGLESv1_CM.so",
            "libGLESv2.so",
            "liblog.so",
            "libm.so",
            "libOpenSLES.so",
            "libvulkan.so",
            "libz.so"
        )
    }
}
