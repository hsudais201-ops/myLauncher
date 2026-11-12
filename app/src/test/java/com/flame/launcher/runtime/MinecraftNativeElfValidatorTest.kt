package com.flame.launcher.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftNativeElfValidatorTest {
    private fun elfHeader(elfClass: Int, machine: Int, bigEndian: Boolean = false): ByteArray {
        val bytes = ByteArray(20)
        bytes[0] = 0x7f
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = elfClass.toByte()
        bytes[5] = if (bigEndian) 2 else 1
        bytes[6] = 1
        bytes[18] = if (bigEndian) (machine shr 8).toByte() else machine.toByte()
        bytes[19] = if (bigEndian) machine.toByte() else (machine shr 8).toByte()
        return bytes
    }

    @Test
    fun acceptsMatchingX8664AndIgnoresNonSharedFiles() {
        val root = Files.createTempDirectory("flame-elf-valid").toFile()
        try {
            val library = File(root, "libtest.so").apply {
                writeBytes(elfHeader(2, 62))
            }
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
            val wrongMachine = File(root, "wrong-machine.so").apply {
                writeBytes(elfHeader(2, 183))
            }
            val wrongClass = File(root, "wrong-class.so").apply {
                writeBytes(elfHeader(1, 62))
            }
            val malformed = File(root, "malformed.so").apply { writeBytes(ByteArray(20)) }
            val result = MinecraftNativeElfValidator().validate(
                listOf(wrongMachine, wrongClass, malformed),
                "x86_64"
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
    fun supportsArm64AndBigEndianHeaders() {
        val root = Files.createTempDirectory("flame-elf-arm").toFile()
        try {
            val library = File(root, "libarm.so").apply {
                writeBytes(elfHeader(2, 183, bigEndian = true))
            }
            assertEquals(
                MinecraftNativeElfValidation.Compatible(1, "arm64"),
                MinecraftNativeElfValidator().validate(listOf(library), "aarch64")
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
