package com.flame.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

class EmbeddedJvmProcessHostTest {
    @Test
    fun builderCreatesEmbeddedInvocationWithoutRequiringJavaExecutable() {
        val root = Files.createTempDirectory("flame-embedded-jvm").toFile()
        try {
            val game = File(root, "game").apply { mkdirs() }
            val natives = File(game, "natives").apply { mkdirs() }
            File(game, "assets").mkdirs()
            File(game, "versions/1.21/1.21.jar").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1))
            }
            val runtimeHome = File(root, "runtime").apply { mkdirs() }
            val jvm = File(runtimeHome, "lib/server/libjvm.so").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(2))
            }
            val runtime = JavaRuntimeInstallation(
                id = "mojo-java-21-arm64",
                version = "21.0.1",
                majorVersion = 21,
                architecture = "arm64",
                homeDirectory = runtimeHome,
                javaExecutable = null,
                jvmLibrary = jvm
            )
            val plan = MinecraftLaunchDependencyPlan(
                versionId = "1.21",
                mainClass = "net.minecraft.client.main.Main",
                requiredJavaMajorVersion = 21,
                assetIndexId = "17",
                downloads = emptyList(),
                classPath = listOf("versions/1.21/1.21.jar"),
                nativeLibraries = emptyList()
            )
            val command = MinecraftLaunchCommandBuilder.build(
                plan,
                game,
                natives,
                runtime,
                MinecraftLaunchAccount(
                    "Player",
                    "12345678-1234-1234-1234-123456789abc",
                    "test-token"
                )
            )

            assertNull(command.executable)
            assertEquals(JavaRuntimeExecutionKind.EMBEDDED_JVM, command.executionKind)
            assertEquals(runtimeHome.canonicalFile, command.runtimeHome)
            assertEquals(jvm.canonicalFile, command.jvmLibrary)
            assertThrows(IllegalStateException::class.java) { command.toProcessBuilder() }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun hostPassesValidatedRuntimeAndArgumentsToNativeBridge() {
        val root = Files.createTempDirectory("flame-embedded-host").toFile()
        try {
            val runtimeHome = File(root, "runtime").apply { mkdirs() }
            val jvm = File(runtimeHome, "lib/server/libjvm.so").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(3))
            }
            val game = File(root, "game").apply { mkdirs() }
            val clientJar = File(game, "client.jar").apply { writeBytes(byteArrayOf(4)) }
            val token = "do-not-log-this-token"
            val command = MinecraftLaunchCommand(
                executable = null,
                workingDirectory = game,
                arguments = listOf(
                    "-Xmx768M",
                    "-Djava.library.path=${File(game, "natives").apply { mkdirs() }.absolutePath}",
                    "-Dorg.lwjgl.librarypath=${File(game, "natives").absolutePath}",
                    "-cp",
                    clientJar.absolutePath,
                    "net.minecraft.client.main.Main",
                    "--accessToken",
                    token
                ),
                classPath = listOf(clientJar.absolutePath),
                runtimeHome = runtimeHome,
                jvmLibrary = jvm,
                executionKind = JavaRuntimeExecutionKind.EMBEDDED_JVM
            )
            val started = AtomicBoolean(false)
            val output = StringBuilder()
            val session = object : AndroidJvmSession {
                override fun awaitExit(): Int = 0
                override fun requestStop() { }
            }
            val bridge = object : AndroidJvmBridge {
                override fun start(
                    runtimeLayout: EmbeddedJvmRuntimeLayout,
                    workingDirectory: File,
                    invocation: EmbeddedJvmInvocation,
                    onOutput: (String) -> Unit
                ): AndroidJvmSession {
                    assertEquals(jvm.canonicalFile, runtimeLayout.jvmLibrary)
                    assertEquals(root.resolve("runtime").canonicalFile, runtimeLayout.runtimeHome)
                    assertTrue(runtimeLayout.libraryDirectories.contains(File(runtimeHome, "lib").canonicalFile))
                    assertTrue(runtimeLayout.libraryDirectories.contains(File(runtimeHome, "lib/server").canonicalFile))
                    assertEquals(File(game, "natives").canonicalFile, runtimeLayout.applicationNativeDirectory)
                    assertTrue(runtimeLayout.libraryDirectories.contains(File(game, "natives").canonicalFile))
                    assertEquals(game.canonicalFile, workingDirectory)
                    assertTrue(invocation.vmOptions.contains("-Xmx768M"))
                    assertTrue(invocation.vmOptions.any { it.startsWith("-Djava.class.path=") })
                    assertTrue(invocation.vmOptions.any { it.startsWith("-Djava.home=") })
                    assertEquals(listOf(clientJar.canonicalFile), invocation.classPath)
                    assertEquals("net.minecraft.client.main.Main", invocation.mainClass)
                    assertEquals(listOf("--accessToken", token), invocation.applicationArguments)
                    onOutput("JVM started")
                    started.set(true)
                    return session
                }
            }

            val running = EmbeddedJvmProcessHost(bridge).start(command) { output.append(it) }
            assertTrue(started.get())
            assertEquals("JVM started", output.toString())
            assertNotNull(running)
            assertEquals(0, running.awaitExit())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun hostRejectsJvmLibraryOutsideRuntimeHome() {
        val root = Files.createTempDirectory("flame-embedded-host-invalid").toFile()
        try {
            val runtimeHome = File(root, "runtime").apply { mkdirs() }
            val outsideJvm = File(root, "libjvm.so").apply { writeBytes(byteArrayOf(1)) }
            val game = File(root, "game").apply { mkdirs() }
            val command = MinecraftLaunchCommand(
                executable = null,
                workingDirectory = game,
                arguments = emptyList(),
                classPath = emptyList(),
                runtimeHome = runtimeHome,
                jvmLibrary = outsideJvm,
                executionKind = JavaRuntimeExecutionKind.EMBEDDED_JVM
            )
            val bridge = object : AndroidJvmBridge {
                override fun start(
                    runtimeLayout: EmbeddedJvmRuntimeLayout,
                    workingDirectory: File,
                    invocation: EmbeddedJvmInvocation,
                    onOutput: (String) -> Unit
                ): AndroidJvmSession = error("Bridge must not be called for an invalid library path.")
            }

            assertThrows(IllegalArgumentException::class.java) {
                EmbeddedJvmProcessHost(bridge).start(command) { }
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
