package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MinecraftLaunchConfigurationResolverTest {
    @Test
    fun combinesOfficialArgumentsAndInstalledClasspathWithoutShellJoining() {
        val root = createTempDir(prefix = "hyper-launch-config-")
        try {
            val client = File(root, "versions/1.21.4/1.21.4.jar")
            client.parentFile?.mkdirs()
            client.writeText("client")
            val hash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            val metadata = """
                {
                  "id":"1.21.4",
                  "mainClass":"net.minecraft.client.main.Main",
                  "downloads":{"client":{"url":"https://piston-data.mojang.com/client.jar","sha1":"$hash","size":1}},
                  "arguments":{
                    "jvm":["-Xmx1G"],
                    "game":["--username","${'$'}{auth_player_name}","--gameDir","${'$'}{game_directory}"]
                  }
                }
            """.trimIndent()
            val details = MinecraftVersionDetailsResolver.parse("1.21.4", metadata)
            val clientFile = PlannedMinecraftFile(
                MinecraftDownload("https://piston-data.mojang.com/client.jar", hash, 1),
                "versions/1.21.4/1.21.4.jar",
                "client"
            )
            val indexFile = PlannedMinecraftFile(
                MinecraftDownload("https://piston-meta.mojang.com/index.json", hash, 1),
                "assets/indexes/19.json",
                "asset-index"
            )
            val plan = MinecraftInstallPlan(
                "1.21.4", clientFile, indexFile, MinecraftAssetIndex("19", emptyList(), 0L),
                emptyList(), emptyList(), emptyList(), 0L
            )

            val config = MinecraftLaunchConfigurationResolver.resolve(
                details, plan, root,
                placeholders = mapOf("auth_player_name" to "Player One")
            )

            assertEquals("net.minecraft.client.main.Main", config.mainClass)
            assertTrue(config.jvmArguments.containsAll(listOf("-Xmx1G", "-cp", config.classpath)))
            assertEquals(
                listOf("--username", "Player One", "--gameDir", root.canonicalPath),
                config.gameArguments
            )
            assertEquals(
                config.jvmArguments + config.mainClass + config.gameArguments,
                config.processArguments()
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
