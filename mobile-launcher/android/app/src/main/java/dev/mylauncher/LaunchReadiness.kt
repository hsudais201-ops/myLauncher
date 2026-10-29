package dev.mylauncher

import android.content.Context
import java.io.File

data class LaunchReadinessReport(
    val clientJarsPresent: Int,
    val savedVersionMetadata: Int,
    val assetIndexes: Int,
    val assetObjects: Int,
    val libraryJars: Int,
    val blockers: List<String>
) {
    val canLaunch: Boolean get() = blockers.isEmpty()

    fun summary(): String = buildString {
        appendLine("Launch status: ${if (canLaunch) "Ready" else "Blocked"}")
        appendLine("Client JARs present: $clientJarsPresent")
        appendLine("Saved version metadata files: $savedVersionMetadata")
        appendLine("Asset indexes present: $assetIndexes")
        appendLine("Asset objects present: $assetObjects")
        appendLine("Library JARs present: $libraryJars")
        if (blockers.isNotEmpty()) {
            appendLine("Still required:")
            blockers.forEach { appendLine("• $it") }
        }
    }.trim()
}

/**
 * Reports local install artifacts only. File presence is not proof of checksum
 * validity, and this deliberately never claims the game can launch until a
 * compatible Java runtime and a complete launch pipeline exist.
 */
object LaunchReadiness {
    fun inspect(context: Context): LaunchReadinessReport {
        val minecraft = File(context.filesDir, "minecraft")
        val versions = File(minecraft, "versions")
        val versionDirs = versions.listFiles()?.filter { it.isDirectory }.orEmpty()
        val clients = versionDirs.count { dir ->
            val id = dir.name
            id.matches(Regex("[A-Za-z0-9._-]{1,100}")) &&
                File(dir, "$id.jar").isFile
        }
        val metadata = versionDirs.count { dir ->
            val id = dir.name
            id.matches(Regex("[A-Za-z0-9._-]{1,100}")) &&
                File(dir, "$id.json").isFile
        }
        val indexes = File(minecraft, "assets/indexes").listFiles()
            ?.count { it.isFile && it.extension.equals("json", ignoreCase = true) } ?: 0
        val assetObjects = countFiles(File(minecraft, "assets/objects"))
        val libraries = countFilesWithExtension(File(minecraft, "libraries"), "jar")
        val blockers = buildList {
            if (clients == 0) add("Download a Minecraft client JAR.")
            if (metadata == 0) add("Save official version metadata.")
            if (indexes == 0) add("Download the asset index.")
            if (assetObjects == 0) add("Download the asset objects.")
            if (libraries == 0) add("Download required library artifacts.")
            add("Install and select a compatible Android Java runtime.")
            add("Resolve native libraries, classpath, JVM arguments, and game launch integration.")
            add("Verify every required artifact for the selected version before launch.")
        }
        return LaunchReadinessReport(clients, metadata, indexes, assetObjects, libraries, blockers)
    }

    private fun countFiles(directory: File): Int {
        if (!directory.isDirectory) return 0
        return directory.walkTopDown().count { it.isFile }
    }

    private fun countFilesWithExtension(directory: File, extension: String): Int {
        if (!directory.isDirectory) return 0
        var count = 0
        directory.walkTopDown().forEach { file ->
            if (file.isFile && file.extension.equals(extension, ignoreCase = true)) count++
        }
        return count
    }
}
