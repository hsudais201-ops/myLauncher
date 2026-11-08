package com.flame.launcher.runtime

import java.io.File

/**
 * Describes the native-library layout expected by an embedded OpenJDK runtime.
 *
 * The order mirrors the runtime's own layout: the JRE lib directory, the
 * selected VM directory, and its optional jli subdirectory. The Android
 * application must add its own extracted Minecraft native directory separately.
 * This is a path plan only; it does not mutate process linker state or load code.
 */
data class EmbeddedJvmRuntimeLayout(
    val runtimeHome: File,
    val jvmLibrary: File,
    val libraryDirectories: List<File>,
    val optionalPreloadLibraries: List<File>
)

object EmbeddedJvmRuntimeLayoutResolver {
    fun resolve(runtimeHome: File, jvmLibrary: File): EmbeddedJvmRuntimeLayout {
        val home = runtimeHome.canonicalFile
        val jvm = jvmLibrary.canonicalFile
        require(home.isDirectory) { "Embedded JVM runtime home is unavailable." }
        require(jvm.isFile && jvm.path.startsWith(home.path + File.separator)) {
            "Embedded JVM library must be a file inside the runtime home."
        }

        val vmDirectory = jvm.parentFile
            ?: throw IllegalArgumentException("Embedded JVM library has no parent directory.")
        val libraryDirectory = vmDirectory.parentFile
            ?: throw IllegalArgumentException("Embedded JVM library directory layout is invalid.")
        require(libraryDirectory.path.startsWith(home.path + File.separator)) {
            "Embedded JVM library directory is outside the runtime home."
        }

        val candidates = listOf(
            libraryDirectory,
            vmDirectory,
            File(libraryDirectory, "jli")
        )
        val libraryDirectories = candidates
            .filter { it.isDirectory }
            .map { it.canonicalFile }
            .distinct()
        require(libraryDirectory.canonicalFile in libraryDirectories) {
            "Embedded JVM runtime library directory is unavailable."
        }

        // MojoLauncher preloads these on Android because class-loader and NIO
        // native resolution can otherwise select incompatible platform copies.
        val optionalPreloadLibraries = listOf(
            File(libraryDirectory, "libawt_headless.so"),
            File(libraryDirectory, "libnio.so")
        ).filter { it.isFile && it.canonicalFile.path.startsWith(home.path + File.separator) }
            .map { it.canonicalFile }

        return EmbeddedJvmRuntimeLayout(
            runtimeHome = home,
            jvmLibrary = jvm,
            libraryDirectories = libraryDirectories,
            optionalPreloadLibraries = optionalPreloadLibraries
        )
    }
}
