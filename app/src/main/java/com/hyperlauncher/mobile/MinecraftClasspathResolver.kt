package com.hyperlauncher.mobile

import java.io.File

/**
 * Builds a platform-correct classpath from installed Minecraft artifacts.
 * Paths are resolved under the selected instance root and never accepted if
 * they escape that root. The returned value is for a single -cp argument.
 */
object MinecraftClasspathResolver {
    fun resolve(
        storageRoot: File,
        clientJarRelativePath: String,
        libraryRelativePaths: List<String>
    ): String {
        val root = storageRoot.canonicalFile
        require(root.exists() && root.isDirectory) { "Minecraft storage root is unavailable" }
        val relativePaths = (listOf(clientJarRelativePath) + libraryRelativePaths).distinct()
        require(relativePaths.isNotEmpty()) { "Minecraft classpath is empty" }
        val files = relativePaths.map { relativePath ->
            require(isSafeRelativePath(relativePath)) { "Minecraft classpath contains an unsafe path" }
            val file = File(root, relativePath).canonicalFile
            require(file.path.startsWith(root.path + File.separator)) {
                "Minecraft classpath path escapes the selected instance"
            }
            require(file.isFile && file.canRead()) {
                "Minecraft classpath file is missing or unreadable: $relativePath"
            }
            file
        }
        return files.joinToString(File.pathSeparator) { it.absolutePath }
    }

    internal fun isSafeRelativePath(value: String): Boolean {
        if (value.isBlank() || value.length > 1024 || value.startsWith("/") ||
            value.contains(92.toChar()) || value.contains('\u0000')) return false
        val segments = value.split('/')
        return segments.all { it.isNotBlank() && it != "." && it != ".." }
    }
}
