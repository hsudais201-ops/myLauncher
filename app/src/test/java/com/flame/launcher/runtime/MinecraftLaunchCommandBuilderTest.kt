package com.flame.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MinecraftLaunchCommandBuilderTest {
    private fun fixture(): Fixture {
        val root = Files.createTempDirectory("flame-launch-command").toFile()
        val game = File(root, "game").apply { mkdirs() }
        val native = File(game, "versions/1.21/natives").apply { mkdirs() }
        val assets = File(game, "assets").apply { mkdirs() }
        val client = File(game, "versions/1.21/1.21.jar").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        val library = File(game, "libraries/org/example/sample.jar").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(2))
        }
        val java = File(root, "runtime/bin/java").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(3))
            setExecutable(true)
        }
        val runtime = JavaRuntimeInstallation(
            id = "java-21",
            version = "21.0.1",
            majorVersion = 21,
            architecture = "arm64",
            homeDirectory = java.parentFile.parentFile,
            javaExecutable = java,
            jvmLibrary = null
        )
        val plan = MinecraftLaunchDependencyPlan(
            versionId = "1.21",
            mainClass = "net.minecraft.client.main.Main",
            requiredJavaMajorVersion = 21,
            assetIndexId = "17",
            downloads = emptyList(),
            classPath = listOf(
                "versions/1.21/1.21.jar",
                "libraries/org/example/sample.jar"
            ),
            nativeLibraries = emptyList()
        )
        return Fixture(root, game, native, runtime, plan)
    }

    @Test
    fun buildsShellFreeCommandWithVerifiedPathsAndAccountArguments() {
        val f = fixture()
        try {
            val token = "test-token-with spaces;not-a-shell-command"
            val command = MinecraftLaunchCommandBuilder.build(
                f.plan, f.game, f.native, f.runtime,
                MinecraftLaunchAccount("Player One", "12345678-1234-1234-1234-123456789abc", token),
                MinecraftLaunchMemory(512, 1536)
            )
            assertEquals(f.runtime.javaExecutable, command.executable)
            assertEquals(f.game.canonicalFile, command.workingDirectory)
            assertTrue(command.arguments.contains("-Xmx1536M"))
            assertTrue(command.arguments.contains("--username"))
            assertTrue(command.arguments.contains("Player One"))
            assertTrue(command.arguments.contains(token))
            assertFalse(command.toString().contains(token))
            assertFalse(MinecraftLaunchAccount("Player", "12345678123412341234123456789abc", token).toString().contains(token))
            assertEquals(2, command.classPath.size)
            val process = command.toProcessBuilder()
            assertEquals(f.game.canonicalFile, process.directory())
            assertFalse(process.command().any { it.contains("sh -c") })
        } finally {
            f.root.deleteRecursively()
        }
    }

    @Test
    fun builderOutputParsesIntoEmbeddedJvmInvocationWithoutLosingGameArguments() {
        val f = fixture()
        try {
            val jvmLibrary = File(f.runtime.homeDirectory, "lib/server/libjvm.so").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(4))
            }
            val embeddedRuntime = f.runtime.copy(jvmLibrary = jvmLibrary)
            val token = "embedded-test-secret"
            val command = MinecraftLaunchCommandBuilder.build(
                f.plan,
                f.game,
                f.native,
                embeddedRuntime,
                MinecraftLaunchAccount(
                    "Player",
                    "12345678-1234-1234-1234-123456789abc",
                    token
                )
            )

            assertEquals(JavaRuntimeExecutionKind.EMBEDDED_JVM, command.executionKind)
            val invocation = EmbeddedJvmInvocationParser.parse(
                arguments = command.arguments,
                expectedClassPath = command.classPath,
                runtimeHome = command.runtimeHome!!,
                workingDirectory = command.workingDirectory
            )
            assertEquals(f.plan.mainClass, invocation.mainClass)
            assertEquals(command.classPath.map { File(it).canonicalFile }, invocation.classPath)
            assertTrue(invocation.applicationArguments.contains("--username"))
            assertTrue(invocation.applicationArguments.contains("Player"))
            assertFalse(invocation.toString().contains(token))
        } finally {
            f.root.deleteRecursively()
        }
    }

    @Test
    fun redactsSeparateAndInlineAccessTokenArgumentForms() {
        val redacted = redactLaunchArguments(
            listOf(
                "--accessToken",
                "separate-secret",
                "--access-token=inline-secret",
                "--username",
                "Player"
            )
        )
        assertEquals(
            listOf("--accessToken", "<redacted>", "--access-token=<redacted>", "--username", "Player"),
            redacted
        )
        assertFalse(redacted.toString().contains("secret"))
    }

    @Test
    fun acceptsSingleCharacterMainClassConsistentlyWithEmbeddedInvocationParser() {
        val f = fixture()
        try {
            val command = MinecraftLaunchCommandBuilder.build(
                f.plan.copy(mainClass = "A"),
                f.game,
                f.native,
                f.runtime,
                MinecraftLaunchAccount("Player", "12345678123412341234123456789abc", "token")
            )
            assertTrue(command.arguments.contains("A"))
        } finally {
            f.root.deleteRecursively()
        }
    }

    @Test
    fun rejectsMalformedMainClassSegments() {
        val f = fixture()
        try {
            listOf("net.minecraft..Main", "net.minecraft.1Main", "net.minecraft.Main.").forEach { mainClass ->
                assertThrows(
                    "Expected rejection for $mainClass",
                    IllegalArgumentException::class.java
                ) {
                    MinecraftLaunchCommandBuilder.build(
                        f.plan.copy(mainClass = mainClass),
                        f.game,
                        f.native,
                        f.runtime,
                        MinecraftLaunchAccount("Player", "12345678123412341234123456789abc", "token")
                    )
                }
            }
        } finally {
            f.root.deleteRecursively()
        }
    }

    @Test
    fun rejectsWrongJavaMajorAndUnsafeClasspathTraversal() {
        val f = fixture()
        try {
            val wrongRuntime = f.runtime.copy(majorVersion = 17)
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftLaunchCommandBuilder.build(
                    f.plan, f.game, f.native, wrongRuntime,
                    MinecraftLaunchAccount("Player", "12345678123412341234123456789abc", "token")
                )
            }
            val unsafePlan = f.plan.copy(classPath = listOf("../outside.jar"))
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftLaunchCommandBuilder.build(
                    unsafePlan, f.game, f.native, f.runtime,
                    MinecraftLaunchAccount("Player", "12345678123412341234123456789abc", "token")
                )
            }
        } finally {
            f.root.deleteRecursively()
        }
    }

    @Test
    fun rejectsInvalidMemoryAndNativeDirectoryOutsideGameRoot() {
        val f = fixture()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftLaunchCommandBuilder.build(
                    f.plan, f.game, f.native, f.runtime,
                    MinecraftLaunchAccount("Player", "12345678123412341234123456789abc", "token"),
                    MinecraftLaunchMemory(2048, 1024)
                )
            }
            val outside = File(f.root, "outside").apply { mkdirs() }
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftLaunchCommandBuilder.build(
                    f.plan, f.game, outside, f.runtime,
                    MinecraftLaunchAccount("Player", "12345678123412341234123456789abc", "token")
                )
            }
        } finally {
            f.root.deleteRecursively()
        }
    }

    private data class Fixture(
        val root: File,
        val game: File,
        val native: File,
        val runtime: JavaRuntimeInstallation,
        val plan: MinecraftLaunchDependencyPlan
    )
}
