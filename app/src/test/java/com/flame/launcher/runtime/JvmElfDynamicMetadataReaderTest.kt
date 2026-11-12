package com.flame.launcher.runtime

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmElfDynamicMetadataReaderTest {
    @Test
    fun readsNeededNamesFromElf64DynamicSection() {
        withElf64(neededOffset = 1, stringTable = byteArrayOf(0, *"libverify.so".toByteArray(), 0)) { file ->
            assertEquals(
                listOf("libverify.so"),
                JvmElfDynamicMetadataReaderImpl().inspect(file).neededLibraries
            )
        }
    }


    @Test
    fun rejectsElfExecutableInsteadOfSharedObject() {
        withElf64(
            neededOffset = 1,
            stringTable = byteArrayOf(0, *"libverify.so".toByteArray(), 0),
            elfType = 2
        ) { file ->
            assertTrue(runCatching { JvmElfDynamicMetadataReaderImpl().inspect(file) }.isFailure)
        }
    }

    @Test
    fun rejectsUnsupportedElfHeaderVersion() {
        withElf64(
            neededOffset = 1,
            stringTable = byteArrayOf(0, *"libverify.so".toByteArray(), 0),
            elfVersion = 2
        ) { file ->
            assertTrue(runCatching { JvmElfDynamicMetadataReaderImpl().inspect(file) }.isFailure)
        }
    }

    @Test
    fun readsNeededNamesFromElf32BigEndianDynamicSection() {
        withElf32BigEndian { file ->
            assertEquals(
                listOf("libverify32.so"),
                JvmElfDynamicMetadataReaderImpl().inspect(file).neededLibraries
            )
        }
    }

    @Test
    fun readsRpathAndRunpathAsMetadataWithoutResolvingThem() {
        val strings = byteArrayOf(0, *"libverify.so".toByteArray(), 0, *"\$ORIGIN/lib".toByteArray(), 0, *"\$ORIGIN/ext".toByteArray(), 0)
        withElf64(neededOffset = 1, stringTable = strings, rpathOffset = 14, runpathOffset = 26) { file ->
            val metadata = JvmElfDynamicMetadataReaderImpl().inspect(file)
            assertEquals(listOf("libverify.so"), metadata.neededLibraries)
            assertEquals(listOf("\$ORIGIN/lib"), metadata.rpathEntries)
            assertEquals(listOf("\$ORIGIN/ext"), metadata.runpathEntries)
        }
    }
    @Test
    fun rejectsNeededStringOffsetOutsideStringTable() {
        withElf64(neededOffset = 90, stringTable = byteArrayOf(0, *"libx.so".toByteArray(), 0)) { file ->
            val result = runCatching { JvmElfDynamicMetadataReaderImpl().inspect(file) }
            assertTrue(result.isFailure)
        }
    }

    @Test
    fun rejectsMissingDynamicTerminator() {
        withElf64(
            neededOffset = 1,
            stringTable = byteArrayOf(0, *"libx.so".toByteArray(), 0),
            terminateDynamic = false
        ) { file ->
            val result = runCatching { JvmElfDynamicMetadataReaderImpl().inspect(file) }
            assertTrue(result.isFailure)
        }
    }


    @Test
    fun rejectsNeededStringWithoutTerminatorInsideStringTable() {
        withElf64(
            neededOffset = 1,
            stringTable = byteArrayOf(0, *"libx.so".toByteArray())
        ) { file ->
            assertTrue(runCatching { JvmElfDynamicMetadataReaderImpl().inspect(file) }.isFailure)
        }
    }

    @Test
    fun rejectsNonElfInput() {
        val file = Files.createTempFile("flame-not-elf", ".bin").toFile()
        try {
            file.writeBytes(ByteArray(64))
            assertTrue(runCatching { JvmElfDynamicMetadataReaderImpl().inspect(file) }.isFailure)
        } finally {
            file.delete()
        }
    }

    private fun withElf64(
        neededOffset: Long,
        stringTable: ByteArray,
        terminateDynamic: Boolean = true,
        rpathOffset: Long? = null,
        runpathOffset: Long? = null,
        elfType: Int = 3,
        elfVersion: Int = 1,
        block: (File) -> Unit
    ) {
        val file = Files.createTempFile("flame-elf64", ".so").toFile()
        try {
            val bytes = ByteArray(0x300)
            val out = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            bytes[0] = 0x7f
            bytes[1] = 'E'.code.toByte()
            bytes[2] = 'L'.code.toByte()
            bytes[3] = 'F'.code.toByte()
            bytes[4] = 2 // ELFCLASS64
            bytes[5] = 1 // ELFDATA2LSB
            bytes[6] = 1 // EV_CURRENT
            out.putShort(16, elfType.toShort()) // ET_DYN by default
            out.putInt(20, elfVersion) // EV_CURRENT by default
            out.putLong(32, 64L) // e_phoff
            out.putShort(52, 64.toShort())
            out.putShort(54, 56.toShort())
            out.putShort(56, 2.toShort())

            // PT_LOAD: the whole synthetic file is mapped at virtual address zero.
            out.putInt(64, 1)
            out.putLong(72, 0L)
            out.putLong(80, 0L)
            out.putLong(96, bytes.size.toLong())

            // PT_DYNAMIC
            out.putInt(120, 2)
            out.putLong(128, 0x180L)
            out.putLong(136, 0x180L)
            val dynamicSize = 64L + (if (rpathOffset != null) 16L else 0L) +
                (if (runpathOffset != null) 16L else 0L)
            out.putLong(152, dynamicSize)

            out.putLong(0x180, 1L) // DT_NEEDED
            out.putLong(0x188, neededOffset)
            out.putLong(0x190, 5L) // DT_STRTAB
            out.putLong(0x198, 0x200L)
            out.putLong(0x1a0, 10L) // DT_STRSZ
            out.putLong(0x1a8, stringTable.size.toLong())
            var dynamicCursor = 0x1b0
            if (rpathOffset != null) {
                out.putLong(dynamicCursor, 15L) // DT_RPATH
                out.putLong(dynamicCursor + 8, rpathOffset)
                dynamicCursor += 16
            }
            if (runpathOffset != null) {
                out.putLong(dynamicCursor, 29L) // DT_RUNPATH
                out.putLong(dynamicCursor + 8, runpathOffset)
                dynamicCursor += 16
            }
            if (terminateDynamic) {
                out.putLong(dynamicCursor, 0L) // DT_NULL
                out.putLong(dynamicCursor + 8, 0L)
            } else {
                out.putLong(dynamicCursor, 1L)
                out.putLong(dynamicCursor + 8, neededOffset)
            }
            stringTable.copyInto(bytes, 0x200)
            file.writeBytes(bytes)
            block(file)
        } finally {
            file.delete()
        }
    }
    private fun withElf32BigEndian(block: (File) -> Unit) {
        val file = Files.createTempFile("flame-elf32-be", ".so").toFile()
        try {
            val bytes = ByteArray(0x240)
            val out = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            bytes[0] = 0x7f
            bytes[1] = 'E'.code.toByte()
            bytes[2] = 'L'.code.toByte()
            bytes[3] = 'F'.code.toByte()
            bytes[4] = 1 // ELFCLASS32
            bytes[5] = 2 // ELFDATA2MSB
            bytes[6] = 1 // EV_CURRENT
            out.putShort(16, 3.toShort()) // ET_DYN
            out.putInt(20, 1) // EV_CURRENT
            out.putInt(28, 52)
            out.putShort(40, 52.toShort())
            out.putShort(42, 32.toShort())
            out.putShort(44, 2.toShort())

            // PT_LOAD maps the synthetic file at virtual address zero.
            out.putInt(52, 1)
            out.putInt(56, 0)
            out.putInt(60, 0)
            out.putInt(68, bytes.size)

            // PT_DYNAMIC
            out.putInt(84, 2)
            out.putInt(88, 0x100)
            out.putInt(92, 0x100)
            out.putInt(100, 32)

            out.putInt(0x100, 1) // DT_NEEDED
            out.putInt(0x104, 1)
            out.putInt(0x108, 5) // DT_STRTAB
            out.putInt(0x10c, 0x180)
            out.putInt(0x110, 10) // DT_STRSZ
            val stringTable = byteArrayOf(0, *"libverify32.so".toByteArray(), 0)
            out.putInt(0x114, stringTable.size)
            out.putInt(0x118, 0) // DT_NULL
            out.putInt(0x11c, 0)
            stringTable.copyInto(bytes, 0x180)
            file.writeBytes(bytes)
            block(file)
        } finally {
            file.delete()
        }
    }

}
