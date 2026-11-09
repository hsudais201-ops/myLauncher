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
            out.putLong(152, 48L)

            out.putLong(0x180, 1L) // DT_NEEDED
            out.putLong(0x188, neededOffset)
            out.putLong(0x190, 5L) // DT_STRTAB
            out.putLong(0x198, 0x200L)
            out.putLong(0x1a0, 10L) // DT_STRSZ
            out.putLong(0x1a8, stringTable.size.toLong())
            if (terminateDynamic) {
                out.putLong(0x1b0, 0L) // DT_NULL
                out.putLong(0x1b8, 0L)
            } else {
                // A full dynamic table with no DT_NULL.
                out.putLong(0x1b0, 1L)
                out.putLong(0x1b8, neededOffset)
            }
            stringTable.copyInto(bytes, 0x200)
            file.writeBytes(bytes)
            block(file)
        } finally {
            file.delete()
        }
    }
}
