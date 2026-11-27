package com.mflegend.flamelauncher.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePathPolicyTest {
    @Test fun acceptsOnlyRuntimeIdsWithSafeCharactersAndLength() {
        assertTrue(RuntimePathPolicy.isRuntimeId("java17-arm64"))
        assertTrue(RuntimePathPolicy.isRuntimeId("runtime_21.0.2"))
        assertFalse(RuntimePathPolicy.isRuntimeId("../outside"))
        assertFalse(RuntimePathPolicy.isRuntimeId(""))
        assertFalse(RuntimePathPolicy.isRuntimeId("x".repeat(81)))
    }

    @Test fun acceptsOnlyCanonicalDirectChildren() {
        val parent = Files.createTempDirectory("flame-runtime-root").toFile()
        try {
            val child = File(parent, "java17").apply { mkdir() }
            val nested = File(child, "nested").apply { mkdir() }
            val sibling = Files.createTempDirectory("flame-runtime-sibling").toFile()
            assertTrue(RuntimePathPolicy.isDirectChild(parent, child))
            assertFalse(RuntimePathPolicy.isDirectChild(parent, nested))
            assertFalse(RuntimePathPolicy.isDirectChild(parent, sibling))
            sibling.deleteRecursively()
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test fun rejectsSymlinkedRuntimeHomeThatEscapesManagedRoot() {
        val parent = Files.createTempDirectory("flame-runtime-root").toFile()
        val outside = Files.createTempDirectory("flame-runtime-outside").toFile()
        try {
            val link = File(parent, "linked-runtime")
            try {
                Files.createSymbolicLink(link.toPath(), outside.toPath())
            } catch (_: UnsupportedOperationException) {
                return
            } catch (_: SecurityException) {
                return
            }
            assertFalse(RuntimePathPolicy.isDirectChild(parent, link))
        } finally {
            parent.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test fun rejectsSymlinkedReleaseFileOutsideRuntimeHome() {
        val home = Files.createTempDirectory("flame-runtime-home").toFile()
        val outside = Files.createTempFile("flame-release", ".txt").toFile()
        try {
            val releaseLink = File(home, "release")
            try {
                Files.createSymbolicLink(releaseLink.toPath(), outside.toPath())
            } catch (_: UnsupportedOperationException) {
                return
            } catch (_: SecurityException) {
                return
            }
            assertFalse(RuntimePathPolicy.isSafeChild(home, releaseLink))
        } finally {
            home.deleteRecursively()
            outside.delete()
        }
    }

    @Test fun acceptsRegularDescendantsInsideRuntimeHome() {
        val home = Files.createTempDirectory("flame-runtime-home").toFile()
        try {
            val libraryDirectory = File(home, "lib/server").apply { mkdirs() }
            val jvm = File(libraryDirectory, "libjvm.so").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            assertTrue(RuntimePathPolicy.isSafeChild(home, jvm))
        } finally {
            home.deleteRecursively()
        }
    }

    @Test fun rejectsSiblingPathsThatShareTheParentNamePrefix() {
        val parent = Files.createTempDirectory("flame-runtime-root").toFile()
        val sibling = File(parent.parentFile, parent.name + "-outside").apply { mkdir() }
        try {
            val candidate = File(sibling, "release").apply { writeText("JAVA_VERSION=17") }
            assertFalse(RuntimePathPolicy.isSafeChild(parent, candidate))
        } finally {
            parent.deleteRecursively()
            sibling.deleteRecursively()
        }
    }

    @Test fun boundsRuntimeDirectoryScanningDeterministically() {
        val children = (0 until 40).map { index ->
            File("/private/java-runtimes/runtime-${index.toString().padStart(2, '0')}")
        }.toTypedArray() + arrayOf(File("/private/java-runtimes/invalid id"))

        val batch = RuntimePathPolicy.selectRuntimeDirectories(children)

        assertEquals(32, batch.directories.size)
        assertEquals(8, batch.omittedCount)
        assertEquals("runtime-00", batch.directories.first().name)
        assertEquals("runtime-31", batch.directories.last().name)
    }

}
