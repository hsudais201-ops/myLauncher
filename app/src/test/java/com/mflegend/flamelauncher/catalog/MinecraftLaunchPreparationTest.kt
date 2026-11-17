package com.mflegend.flamelauncher.catalog

import com.mflegend.flamelauncher.runtime.AndroidJavaRuntimeManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftLaunchPreparationTest {
    private val android = MinecraftLaunchRuleContext(MinecraftLaunchPlatform.ANDROID)

    @Test
    fun preparesModernArgumentsAndSelectsExactJavaRuntime() {
        val metadata = """
            {
              "id": "1.21.4",
              "mainClass": "net.minecraft.client.main.Main",
              "javaVersion": {"component": "java-runtime-gamma", "majorVersion": 21},
              "arguments": {
                "jvm": ["-Djava.library.path=${'$'}{natives_directory}"],
                "game": ["--username", "${'$'}{auth_player_name}"]
              }
            }
        """.trimIndent().toByteArray()
        val runtime = runtime("java-21-arm64", 21, true)

        val plan = MinecraftLaunchPreparationBuilder.prepare(
            metadata,
            "1.21.4",
            android,
            mapOf(
                "natives_directory" to "/private/natives",
                "auth_player_name" to "Player One"
            ),
            listOf(runtime)
        )

        assertEquals("net.minecraft.client.main.Main", plan.mainClass)
        assertEquals(21, plan.requiredJavaMajorVersion)
        assertEquals("java-21-arm64", plan.selectedRuntimeId)
        assertEquals(listOf("-Djava.library.path=/private/natives"), plan.jvmArguments)
        assertEquals(listOf("--username", "Player One"), plan.gameArguments)
        assertTrue(plan.hasExactRuntimeCandidate)
        assertFalse(plan.blockers.isEmpty())
    }

    @Test
    fun refusesWrongJavaMajorOrWrongAbiCandidates() {
        val metadata = """
            {
              "id": "1.21.4",
              "mainClass": "net.minecraft.client.main.Main",
              "javaVersion": {"component": "java-runtime-gamma", "majorVersion": 21},
              "arguments": {"game": ["--demo"]}
            }
        """.trimIndent().toByteArray()

        val plan = MinecraftLaunchPreparationBuilder.prepare(
            metadata,
            "1.21.4",
            android,
            emptyMap(),
            listOf(runtime("java-17", 17, true), runtime("java-21-other-abi", 21, false))
        )

        assertNull(plan.selectedRuntimeId)
        assertFalse(plan.hasExactRuntimeCandidate)
        assertTrue(plan.blockers.any { it.contains("exactly matches Java 21") })
    }

    @Test
    fun legacyVersionsRequireExplicitFallbackJavaMajor() {
        val metadata = """
            {
              "id": "1.12.2",
              "mainClass": "net.minecraft.client.main.Main",
              "minecraftArguments": "--username ${'$'}{auth_player_name}"
            }
        """.trimIndent().toByteArray()

        val withoutFallback = MinecraftLaunchPreparationBuilder.prepare(
            metadata, "1.12.2", android, mapOf("auth_player_name" to "Player"), emptyList()
        )
        val withFallback = MinecraftLaunchPreparationBuilder.prepare(
            metadata, "1.12.2", android, mapOf("auth_player_name" to "Player"), listOf(runtime("java-8", 8, true)), 8
        )

        assertNull(withoutFallback.requiredJavaMajorVersion)
        assertTrue(withoutFallback.blockers.any { it.contains("does not declare a Java major") })
        assertEquals(8, withFallback.requiredJavaMajorVersion)
        assertEquals("java-8", withFallback.selectedRuntimeId)
        assertEquals(listOf("--username", "Player"), withFallback.gameArguments)
    }

    @Test
    fun rejectsInvalidLegacyFallbackJavaMajor() {
        val metadata = """
            {
              "id": "1.12.2",
              "mainClass": "net.minecraft.client.main.Main",
              "minecraftArguments": "--demo"
            }
        """.trimIndent().toByteArray()

        val error = runCatching {
            MinecraftLaunchPreparationBuilder.prepare(
                metadata, "1.12.2", android, emptyMap(), emptyList(), 7
            )
        }.exceptionOrNull()

        assertTrue(error != null)
    }

    private fun runtime(id: String, major: Int, matchesAbi: Boolean) =
        AndroidJavaRuntimeManager.InstalledRuntime(
            id = id,
            javaVersion = "$major.0.0",
            javaMajorVersion = major,
            architecture = "arm64",
            homeDirectory = File("/runtime/$id"),
            jvmLibrary = File("/runtime/$id/lib/server/libjvm.so"),
            matchesProcessAbi = matchesAbi
        )
}
