package com.flame.launcher.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftNativeElfValidatorTest {
    private fun elfHeader(
        elfClass: Int,
        machine: Int,
        bigEndian: Boolean = false,
        type: Int = 3,
        version: Int = 1,
        declaredHeaderSize: Int? = null
    ): ByteArray {
        val size = if (elfClass == 1) 52 else 64
        val bytes = ByteArray(size)
        bytes[0] = 0x7f
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = elfClass.toByte()
        bytes[5] = if (bigEndian) 2 else 1
        bytes[6] = 1
        put16(bytes, 16, type, bigEndian)
        put16(bytes, 18, machine, bigEndian)
        put32(bytes, 20, version, bigEndian)
        put16(bytes, if (elfClass == 1) 40 else 52, declaredHeaderSize ?: size, bigEndian)
        return bytes
    }

    private fun put16(bytes: ByteArray, offset: Int, value: Int, bigEndian: Boolean) {
        bytes[offset] = if (bigEndian) (value shr 8).toByte() else value.toByte()
        bytes[offset + 1] = if (bigEndian) value.toByte() else (value shr 8).toByte()
    }

    private fun put32(bytes: ByteArray, offset: Int, value: Int, bigEndian: Boolean) {
        for (index in 0..3) {
            val shift = if (bigEndian) (3 - index) * 8 else index * 8
            bytes[offset + index] = (value ushr shift).toByte()
        }
    }

    @Test
    fun acceptsMatchingX8664AndIgnoresNonSharedFiles() {
        val root = Files.createTempDirectory("flame-elf-valid").toFile()
        try {
            val library = File(root, "libtest.so").apply { writeBytes(elfHeader(2, 62)) }
            val resource = File(root, "readme.txt").apply { writeText("not an ELF") }
            assertEquals(
                MinecraftNativeElfValidation.Compatible(1, "x86_64"),
                MinecraftNativeElfValidator().validate(listOf(library, resource), "x86_64")
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsWrongMachineClassAndMalformedHeaders() {
        val root = Files.createTempDirectory("flame-elf-invalid").toFile()
        try {
            val wrongMachine = File(root, "wrong-machine.so").apply { writeBytes(elfHeader(2, 183)) }
            val wrongClass = File(root, "wrong-class.so").apply { writeBytes(elfHeader(1, 62)) }
            val malformed = File(root, "malformed.so").apply { writeBytes(ByteArray(20)) }
            val result = MinecraftNativeElfValidator().validate(
                listOf(wrongMachine, wrongClass, malformed), "x86_64"
            )
            assertTrue(result is MinecraftNativeElfValidation.Incompatible)
            assertEquals(
                listOf(wrongMachine.path, wrongClass.path, malformed.path),
                (result as MinecraftNativeElfValidation.Incompatible).files
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsTruncatedHeadersAndInvalidSharedObjectMetadata() {
        val root = Files.createTempDirectory("flame-elf-truncated").toFile()
        try {
            val valid = elfHeader(2, 62)
            val truncated = File(root, "truncated.so").apply { writeBytes(valid.copyOf(20)) }
            val wrongType = File(root, "wrong-type.so").apply { writeBytes(elfHeader(2, 62, type = 2)) }
            val wrongVersion = File(root, "wrong-version.so").apply { writeBytes(elfHeader(2, 62, version = 2)) }
            val wrongSize = File(root, "wrong-size.so").apply {
                writeBytes(elfHeader(2, 62, declaredHeaderSize = 52))
            }
            val result = MinecraftNativeElfValidator().validate(
                listOf(truncated, wrongType, wrongVersion, wrongSize), "x86_64"
            )
            assertTrue(result is MinecraftNativeElfValidation.Incompatible)
            assertEquals(4, (result as MinecraftNativeElfValidation.Incompatible).files.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun supportsArm64BigEndianAndElf32() {
        val root = Files.createTempDirectory("flame-elf-variants").toFile()
        try {
            val arm64 = File(root, "libarm64.so").apply {
                writeBytes(elfHeader(2, 183, bigEndian = true))
            }
            val arm32 = File(root, "libarm32.so").apply { writeBytes(elfHeader(1, 40)) }
            assertEquals(
                MinecraftNativeElfValidation.Compatible(1, "arm64"),
                MinecraftNativeElfValidator().validate(listOf(arm64), "aarch64")
            )
            assertEquals(
                MinecraftNativeElfValidation.Compatible(1, "arm"),
                MinecraftNativeElfValidator().validate(listOf(arm32), "armeabi-v7a")
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsUnknownDeviceArchitecture() {
        val result = MinecraftNativeElfValidator().validate(emptyList(), "mips")
        assertTrue(result is MinecraftNativeElfValidation.UnsupportedArchitecture)
    }
}
