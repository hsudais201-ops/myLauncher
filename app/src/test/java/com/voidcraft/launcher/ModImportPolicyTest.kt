package com.voidcraft.launcher

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModImportPolicyTest {
    @Test
    fun copiesNonEmptyModWithinTheLimit() {
        val root = Files.createTempDirectory("voidcraft-mod-import").toFile()
        try {
            val destination = File(root, "valid.part")
            val bytes = byteArrayOf(1, 2, 3, 4)
            assertEquals(bytes.size.toLong(), copyModArchiveToTemporaryFile(ByteArrayInputStream(bytes), destination, 4L))
            assertTrue(destination.exists())
            assertEquals(bytes.size.toLong(), destination.length())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun removesPartialFileWhenInputExceedsTheLimit() {
        val root = Files.createTempDirectory("voidcraft-mod-import-limit").toFile()
        try {
            val destination = File(root, "oversized.part")
            val error = runCatching {
                copyModArchiveToTemporaryFile(ByteArrayInputStream(ByteArray(6)), destination, 5L)
            }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertFalse(destination.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun removesPartialFileWhenInputIsEmpty() {
        val root = Files.createTempDirectory("voidcraft-mod-import-empty").toFile()
        try {
            val destination = File(root, "empty.part")
            val error = runCatching {
                copyModArchiveToTemporaryFile(ByteArrayInputStream(byteArrayOf()), destination, 5L)
            }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertFalse(destination.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
