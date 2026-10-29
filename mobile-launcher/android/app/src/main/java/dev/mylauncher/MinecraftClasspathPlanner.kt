package dev.mylauncher

import java.io.File

/** Result of validating the local JARs required to assemble a version classpath. */
data class MinecraftClasspathResult(
    val classpath: List<String>,
    val missingArtifacts: List<String>
) {
    val complete: Boolean get() = missingArtifacts.isEmpty() && classpath.isNotEmpty()
    fun asClasspathString(): String = classpath.joinToString(File.pathSeparator)
}

/**
 * Builds a classpath from the parsed version plan and app-private game directory.
 * This does not verify hashes again, select a Java runtime, or launch a process.
 */
object MinecraftClasspathPlanner {
    fun build(gameDirectory: File, plan: MinecraftInstallPlan): MinecraftClasspathResult {
        require(plan.versionId.matches(Regex("[A-Za-z0-9._-]{1,100}"))) { "Invalid version ID." }
        val root = gameDirectory.canonicalFile
        val versionDirectory = File(File(root, "versions"), plan.versionId).canonicalFile
        require(versionDirectory.toPath().startsWith(root.toPath())) { "Version path escapes game directory." }
        val clientJar = File(versionDirectory, "${plan.versionId}.jar").canonicalFile
        require(clientJar.toPath().startsWith(root.toPath())) { "Client JAR path escapes game directory." }

        val classpath = mutableListOf<String>()
        val missing = mutableListOf<String>()
        if (clientJar.isFile && clientJar.length() > 0L) classpath += clientJar.absolutePath
        else missing += "versions/${plan.versionId}/${plan.versionId}.jar"

        for (library in plan.libraries) {
            val relative = library.relativePath
            if (relative.isBlank() || relative.startsWith("/") || relative.contains('\\') ||
                relative.split('/').any { it.isBlank() || it == "." || it == ".." }) {
                missing += "Invalid library path: $relative"
                continue
            }
            val artifact = File(root, "libraries/$relative").canonicalFile
            if (!artifact.toPath().startsWith(root.toPath())) {
                missing += "Invalid library path: $relative"
            } else if (artifact.isFile && artifact.length() > 0L) {
                classpath += artifact.absolutePath
            } else {
                missing += "libraries/$relative"
            }
        }
        return MinecraftClasspathResult(classpath.distinct(), missing.distinct())
    }
}
