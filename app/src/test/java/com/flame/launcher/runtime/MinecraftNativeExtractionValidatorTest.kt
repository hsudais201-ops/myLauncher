package com.flame.launcher.runtime

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftNativeExtractionValidatorTest {
    private fun elfHeader(): ByteArray = ByteArray(64).apply {
        this[0] = 0x7f
        this[1] = 'E'.code.toByte()
        this[2] = 'L'.code.toByte()
        this[3] = 'F'.code.toByte()
        this[4] = 2
        this[5] = 1
        this[6] = 1
        this[16] = 3
        this[18] = 62
        this[20] = 1
        this[52] = 64
    }

    @Test
    fun acceptsExactExtractedFileSetAndReportsHashes() {
        val root = Files.createTempDirectory("flame-native-verify-").toFile()
        try {
            val first = File(root, "org/lwjgl/liblwjgl.so").apply {
                parentFile.mkdirs()
                writeBytes(elfHeader())
            }
            val second = File(root, "libglfw.so").apply { writeBytes(elfHeader()) }
            val result = MinecraftNativeExtractionValidator().validate(root, listOf(first, second))
            assertTrue(result is MinecraftNativeExtractionValidation.Complete)
            val complete = result as MinecraftNativeExtractionValidation.Complete
            assertEquals(2, complete.verifiedFileCount)
            assertEquals(128L, complete.verifiedBytes)
            assertEquals(
                MessageDigest.getInstance("SHA-1").digest(elfHeader())
                    .joinToString("") { "%02x".format(it) },
                complete.sha1ByPath["org/lwjgl/liblwjgl.so"]
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun reportsMissingAndUnexpectedFiles() {
        val root = Files.createTempDirectory("flame-native-diff-").toFile()
        try {
            val expected = File(root, "libexpected.so").apply { writeBytes(byteArrayOf(1)) }
            File(root, "libunexpected.so").writeBytes(byteArrayOf(2))
            expected.delete()
            val result = MinecraftNativeExtractionValidator().validate(root, listOf(expected))
            assertTrue(result is MinecraftNativeExtractionValidation.Incomplete)
            val incomplete = result as MinecraftNativeExtractionValidation.Incomplete
            assertEquals(listOf("libexpected.so"), incomplete.missingFiles)
            assertEquals(listOf("libunexpected.so"), incomplete.unexpectedFiles)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsDuplicateExpectedPathsAndSymbolicLinks() {
        val root = Files.createTempDirectory("flame-native-links-").toFile()
        val outside = Files.createTempFile("flame-native-outside-", ".so")
        try {
            val link = File(root, "liblink.so")
            Files.createSymbolicLink(link.toPath(), outside)
            val validator = MinecraftNativeExtractionValidator()
            assertEquals(
                MinecraftNativeExtractionValidation.InvalidPlan,
                validator.validate(root, listOf(link))
            )
            val regular = File(root, "libregular.so").apply { writeBytes(byteArrayOf(7)) }
            assertEquals(
                MinecraftNativeExtractionValidation.InvalidPlan,
                validator.validate(root, listOf(regular, regular))
            )
        } finally {
            root.deleteRecursively()
            Files.deleteIfExists(outside)
        }
    }
}
