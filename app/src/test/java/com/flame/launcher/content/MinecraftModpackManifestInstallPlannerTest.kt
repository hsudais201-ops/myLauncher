package com.flame.launcher.content

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftModpackManifestInstallPlannerTest {
    private fun remote(
        path: String,
        client: String = "required"
    ) = MinecraftModpackManifestParser.RemoteFile(
        path = path,
        downloads = listOf("https://cdn.modrinth.com/data/example/file"),
        sha1 = "a".repeat(40),
        sha512 = null,
        fileSizeBytes = 128,
        clientEnvironment = client,
        serverEnvironment = "required"
    )

    private fun manifest(
        version: String = "1.20.1",
        loaders: Map<String, String> = mapOf("fabric-loader" to "0.15.0"),
        files: List<MinecraftModpackManifestParser.RemoteFile> = listOf(remote("mods/example.jar"))
    ) = MinecraftModpackManifestParser.Manifest("Test pack", "test", version, loaders, files)

    private fun target(root: File) = MinecraftInstanceTarget(
        minecraftVersion = "1.20.1",
        loader = MinecraftLoader.FABRIC,
        contentDirectory = root.absolutePath
    )

    @Test
    fun separatesRequiredOptionalAndUnsupportedClientFiles() {
        val root = createTempDir(prefix = "flame-pack-plan-")
        try {
            val result = MinecraftModpackManifestInstallPlanner().plan(
                manifest(files = listOf(
                    remote("mods/required.jar"),
                    remote("mods/optional.jar", "optional"),
                    remote("mods/server-only.jar", "unsupported")
                )),
                target(root)
            )
            assertTrue(result is MinecraftModpackManifestInstallPlanner.Result.Ready)
            val plan = (result as MinecraftModpackManifestInstallPlanner.Result.Ready).plan
            assertEquals(listOf("mods/required.jar"), plan.requiredFiles.map { it.relativePath })
            assertEquals(listOf("mods/optional.jar"), plan.optionalFiles.map { it.relativePath })
            assertEquals(1, plan.skippedClientUnsupported)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsMinecraftVersionMismatch() {
        val root = createTempDir(prefix = "flame-pack-plan-")
        try {
            val result = MinecraftModpackManifestInstallPlanner().plan(
                manifest(version = "1.21.1"), target(root)
            )
            assertTrue(result is MinecraftModpackManifestInstallPlanner.Result.Rejected)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsLoaderMismatch() {
        val root = createTempDir(prefix = "flame-pack-plan-")
        try {
            val result = MinecraftModpackManifestInstallPlanner().plan(
                manifest(loaders = mapOf("forge" to "47.2.0")), target(root)
            )
            assertTrue(result is MinecraftModpackManifestInstallPlanner.Result.Rejected)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsRemoteFileCollisionWithOverride() {
        val root = createTempDir(prefix = "flame-pack-plan-")
        try {
            val result = MinecraftModpackManifestInstallPlanner().plan(
                manifest(files = listOf(remote("config/options.txt"))),
                target(root),
                overrideFiles = listOf("overrides/config/options.txt")
            )
            assertTrue(result is MinecraftModpackManifestInstallPlanner.Result.Rejected)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsFileDirectoryPathConflict() {
        val root = createTempDir(prefix = "flame-pack-plan-")
        try {
            val result = MinecraftModpackManifestInstallPlanner().plan(
                manifest(files = listOf(
                    remote("config"),
                    remote("config/options.txt")
                )),
                target(root)
            )
            assertTrue(result is MinecraftModpackManifestInstallPlanner.Result.Rejected)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsUnsafeOverridePath() {
        val root = createTempDir(prefix = "flame-pack-plan-")
        try {
            val result = MinecraftModpackManifestInstallPlanner().plan(
                manifest(), target(root),
                overrideFiles = listOf("overrides/../../outside.txt")
            )
            assertTrue(result is MinecraftModpackManifestInstallPlanner.Result.Rejected)
        } finally {
            root.deleteRecursively()
        }
    }
}
