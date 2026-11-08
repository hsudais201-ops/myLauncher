package com.flame.launcher.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class EmbeddedJvmInvocationParserTest {
    @Test
    fun convertsClasspathAndSeparatesMainClassFromGameArguments() {
        val root = Files.createTempDirectory("flame-jvm-args").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val game = File(root, "game").apply { mkdirs() }
            val jar = File(game, "client.jar").apply { writeBytes(byteArrayOf(1)) }

            val parsed = EmbeddedJvmInvocationParser.parse(
                arguments = listOf(
                    "-Xms512M",
                    "-Xmx1536M",
                    "-Djava.library.path=/tmp/natives",
                    "-cp",
                    jar.absolutePath,
                    "net.minecraft.client.main.Main",
                    "--username",
                    "Player"
                ),
                expectedClassPath = listOf(jar.absolutePath),
                runtimeHome = home,
                workingDirectory = game
            )

            assertEquals(listOf(jar.canonicalFile), parsed.classPath)
            assertEquals("net.minecraft.client.main.Main", parsed.mainClass)
            assertEquals(listOf("--username", "Player"), parsed.applicationArguments)
            assertEquals("-Xms512M", parsed.vmOptions[0])
            assertEquals("-Xmx1536M", parsed.vmOptions[1])
            assertEquals(
                "-Djava.class.path=" + jar.canonicalPath,
                parsed.vmOptions[3]
            )
            assertEquals("-Djava.home=" + home.canonicalPath, parsed.vmOptions[4])
            assertEquals("-Duser.dir=" + game.canonicalPath, parsed.vmOptions[5])
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsMissingOrDuplicateClasspathSwitches() {
        val root = Files.createTempDirectory("flame-jvm-args-invalid").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val game = File(root, "game").apply { mkdirs() }
            val jar = File(game, "client.jar").apply { writeBytes(byteArrayOf(1)) }

            assertThrows(IllegalArgumentException::class.java) {
                parse(listOf("-Xmx1G", "example.Main"), jar, home, game)
            }
            assertThrows(IllegalArgumentException::class.java) {
                parse(
                    listOf("-cp", jar.absolutePath, "-classpath", jar.absolutePath, "example.Main"),
                    jar,
                    home,
                    game
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsClasspathMismatchAndMissingArtifacts() {
        val root = Files.createTempDirectory("flame-jvm-args-mismatch").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val game = File(root, "game").apply { mkdirs() }
            val jar = File(game, "client.jar").apply { writeBytes(byteArrayOf(1)) }
            val other = File(game, "other.jar").apply { writeBytes(byteArrayOf(2)) }

            assertThrows(IllegalArgumentException::class.java) {
                EmbeddedJvmInvocationParser.parse(
                    listOf("-cp", other.absolutePath, "example.Main"),
                    listOf(jar.absolutePath),
                    home,
                    game
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                parse(
                    listOf("-cp", File(game, "missing.jar").absolutePath, "example.Main"),
                    File(game, "missing.jar"),
                    home,
                    game
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsInvalidMainClassAndEmptyClasspathEntries() {
        val root = Files.createTempDirectory("flame-jvm-args-shape").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val game = File(root, "game").apply { mkdirs() }
            val jar = File(game, "client.jar").apply { writeBytes(byteArrayOf(1)) }
            assertThrows(IllegalArgumentException::class.java) {
                parse(listOf("-cp", jar.absolutePath, "-invalid"), jar, home, game)
            }
            assertThrows(IllegalArgumentException::class.java) {
                parse(
                    listOf("-cp", jar.absolutePath + File.pathSeparator, "example.Main"),
                    jar,
                    home,
                    game
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsLauncherManagedPropertiesAndAcceptsSingleCharacterMainClass() {
        val root = Files.createTempDirectory("flame-jvm-managed-properties").toFile()
        try {
            val home = File(root, "runtime").apply { mkdirs() }
            val game = File(root, "game").apply { mkdirs() }
            val jar = File(game, "client.jar").apply { writeBytes(byteArrayOf(1)) }

            assertThrows(IllegalArgumentException::class.java) {
                parse(
                    listOf("-Djava.class.path=/unexpected.jar", "-cp", jar.absolutePath, "example.Main"),
                    jar,
                    home,
                    game
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                parse(
                    listOf("-Djava.home=/unexpected", "-cp", jar.absolutePath, "example.Main"),
                    jar,
                    home,
                    game
                )
            }

            val parsed = parse(
                listOf("-Xmx512M", "-cp", jar.absolutePath, "A"),
                jar,
                home,
                game
            )
            assertEquals("A", parsed.mainClass)
            val invocationWithToken = parsed.copy(
                applicationArguments = listOf("--accessToken", "sensitive-test-token")
            )
            assertFalse(invocationWithToken.toString().contains("sensitive-test-token"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun parse(
        arguments: List<String>,
        jar: File,
        home: File,
        game: File
    ) = EmbeddedJvmInvocationParser.parse(
        arguments,
        listOf(jar.absolutePath),
        home,
        game
    )
}
