package com.mflegend.flamelauncher.runtime

import java.io.File
import java.nio.file.Files
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeImportPolicyTest {
    @Test
    fun acceptsSafeRuntimePathSegments() {
        RuntimeImportPolicy.validateRelativePath(listOf("lib", "server", "libjvm.so"))
        RuntimeImportPolicy.validateRelativePath(listOf("conf", "security", "java.security"))
    }

    @Test
    fun rejectsUnsafeRuntimePathSegments() {
        listOf("", ".", "..", "../escape", "a/b", "a\\b", "has space", "bad" + 0.toChar())
            .forEach { name ->
                assertFalse(runCatching { RuntimeImportPolicy.validateEntryName(name) }.isSuccess)
            }
        assertFalse(runCatching { RuntimeImportPolicy.validateRelativePath(emptyList()) }.isSuccess)
        assertFalse(runCatching {
            RuntimeImportPolicy.validateRelativePath((1..33).map { "d$it" })
        }.isSuccess)
    }

    @Test
    fun rejectsTooLongFileNames() {
        assertFalse(runCatching {
            RuntimeImportPolicy.validateEntryName("x".repeat(256))
        }.isSuccess)
    }

    @Test
    fun confirmsCanonicalContainment() {
        val root = Files.createTempDirectory("flame-import-root").toFile()
        try {
            val child = File(root, "runtime").apply { mkdir() }
            val sibling = File(root.parentFile, root.name + "-outside").apply { mkdir() }
            assertTrue(RuntimeImportPolicy.isContained(root, child))
            assertFalse(RuntimeImportPolicy.isContained(root, sibling))
            sibling.deleteRecursively()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun safeChildAcceptsOrdinaryDescendants() {
        val root = Files.createTempDirectory("flame-safe-child").toFile()
        try {
            val nested = File(root, "lib/server").apply { mkdirs() }
            val library = File(nested, "libjvm.so").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            assertTrue(RuntimePathPolicy.isSafeChild(root, library))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun safeChildRejectsSymbolicLinksEvenWhenTheyPointInsideRoot() {
        val root = Files.createTempDirectory("flame-safe-link").toFile()
        try {
            val actual = File(root, "actual").apply { mkdir() }
            val target = File(actual, "release").apply { writeText("JAVA_VERSION=21") }
            val link = File(root, "linked-release")
            Files.createSymbolicLink(link.toPath(), target.toPath())
            assertFalse(RuntimePathPolicy.isSafeChild(root, link))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun safeChildRejectsSymbolicLinkInAnIntermediateDirectory() {
        val root = Files.createTempDirectory("flame-safe-parent-link").toFile()
        try {
            val actual = File(root, "actual").apply { mkdir() }
            val target = File(actual, "release").apply { writeText("JAVA_VERSION=21") }
            val linkDirectory = File(root, "linked")
            Files.createSymbolicLink(linkDirectory.toPath(), actual.toPath())
            assertFalse(RuntimePathPolicy.isSafeChild(root, File(linkDirectory, target.name)))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun readsMetadataAtTheConfiguredByteLimit() {
        val directory = Files.createTempDirectory("flame-release-limit").toFile()
        val metadata = File(directory, "release").apply { writeBytes(byteArrayOf(0x41, 0x42, 0x43)) }
        try {
            assertTrue(RuntimeImportPolicy.readBoundedText(metadata, 3L) == "ABC")
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsMetadataLargerThanTheConfiguredByteLimit() {
        val directory = Files.createTempDirectory("flame-release-oversize").toFile()
        val metadata = File(directory, "release").apply { writeBytes(byteArrayOf(0x41, 0x42, 0x43, 0x44)) }
        try {
            val error = runCatching {
                RuntimeImportPolicy.readBoundedText(metadata, 3L)
            }.exceptionOrNull()
            assertTrue(error is IOException)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsMalformedUtf8Metadata() {
        val directory = Files.createTempDirectory("flame-release-invalid-utf8").toFile()
        val metadata = File(directory, "release").apply { writeBytes(byteArrayOf(0xC3.toByte(), 0x28)) }
        try {
            val error = runCatching {
                RuntimeImportPolicy.readBoundedText(metadata, 16L)
            }.exceptionOrNull()
            assertTrue(error is java.nio.charset.CharacterCodingException)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsNegativeMetadataReadLimit() {
        val directory = Files.createTempDirectory("flame-release-negative").toFile()
        val metadata = File(directory, "release").apply { writeText("JAVA_VERSION=21") }
        try {
            assertFalse(runCatching {
                RuntimeImportPolicy.readBoundedText(metadata, -1L)
            }.isSuccess)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun recognizesArm64Elf64HeaderOnly() {
        val header = ByteArray(20)
        header[0] = 0x7f
        header[1] = 'E'.code.toByte()
        header[2] = 'L'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = 2
        header[5] = 1
        header[18] = 183.toByte()
        assertTrue(RuntimeBinaryPolicy.isArm64Elf64(header))
        assertFalse(RuntimeBinaryPolicy.isArm64Elf64(header.copyOf(19)))
        assertFalse(RuntimeBinaryPolicy.isArm64Elf64(header.copyOf().apply { this[4] = 1 }))
        assertFalse(RuntimeBinaryPolicy.isArm64Elf64(header.copyOf().apply { this[18] = 62 }))
    }
}
