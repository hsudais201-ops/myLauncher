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
