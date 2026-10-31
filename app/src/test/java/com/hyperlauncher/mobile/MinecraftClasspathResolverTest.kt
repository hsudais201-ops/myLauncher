package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class MinecraftClasspathResolverTest {
    @Test
    fun buildsClasspathWithSeparatePathsAndDeduplicatesArtifacts() {
        val root = createTempDir(prefix = "hyper-classpath-")
        try {
            val client = File(root, "versions/1.21.4/client.jar")
            val library = File(root, "libraries/example/library.jar")
            client.parentFile?.mkdirs()
            library.parentFile?.mkdirs()
            client.writeText("client")
            library.writeText("library")

            val result = MinecraftClasspathResolver.resolve(
                root,
                "versions/1.21.4/client.jar",
                listOf("libraries/example/library.jar", "libraries/example/library.jar")
            )

            assertEquals(
                listOf(client.canonicalPath, library.canonicalPath).joinToString(File.pathSeparator),
                result
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsTraversalAndMissingArtifacts() {
        val root = createTempDir(prefix = "hyper-classpath-")
        try {
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftClasspathResolver.resolve(root, "../outside.jar", emptyList())
            }
            assertThrows(IllegalArgumentException::class.java) {
                MinecraftClasspathResolver.resolve(root, "versions/missing.jar", emptyList())
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
