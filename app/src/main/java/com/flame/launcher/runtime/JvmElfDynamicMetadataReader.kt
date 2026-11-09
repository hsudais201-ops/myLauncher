package com.flame.launcher.runtime

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Reads DT_NEEDED names from an ELF file without mapping or loading it.
 *
 * Supports ELF32/ELF64 and either byte order. All file offsets and virtual
 * address translations are checked against the file-backed PT_LOAD segments.
 */
class JvmElfDynamicMetadataReaderImpl : JvmElfDynamicMetadataReader {
    override fun inspect(elfFile: File): JvmElfDynamicMetadata {
        require(elfFile.isFile) { "ELF input is not a regular file." }
        RandomAccessFile(elfFile, "r").use { file ->
            val length = file.length()
            require(length >= ELF_IDENT_SIZE) { "ELF header is truncated." }
            val ident = readBytes(file, 0, ELF_IDENT_SIZE, length)
            require(
                ident[0] == 0x7f.toByte() && ident[1] == 'E'.code.toByte() &&
                    ident[2] == 'L'.code.toByte() && ident[3] == 'F'.code.toByte()
            ) { "Invalid ELF magic." }

            val elfClass = ident[4].toInt() and 0xff
            val dataEncoding = ident[5].toInt() and 0xff
            require(elfClass == ELFCLASS32 || elfClass == ELFCLASS64) {
                "Unsupported ELF class: $elfClass."
            }
            require(dataEncoding == ELFDATA2LSB || dataEncoding == ELFDATA2MSB) {
                "Unsupported ELF byte order: $dataEncoding."
            }
            require((ident[6].toInt() and 0xff) == EV_CURRENT) {
                "Unsupported ELF identification version."
            }

            val order = if (dataEncoding == ELFDATA2LSB) {
                ByteOrder.LITTLE_ENDIAN
            } else {
                ByteOrder.BIG_ENDIAN
            }
            val headerSize = if (elfClass == ELFCLASS32) ELF32_HEADER_SIZE else ELF64_HEADER_SIZE
            require(length >= headerSize) { "ELF header is truncated." }
            val header = buffer(readBytes(file, 0, headerSize, length), order)
            val phoff = if (elfClass == ELFCLASS32) header.u32(28) else header.u64(32)
            val ehsize = header.u16(if (elfClass == ELFCLASS32) 40 else 52)
            val phentsize = header.u16(if (elfClass == ELFCLASS32) 42 else 54)
            val phnum = header.u16(if (elfClass == ELFCLASS32) 44 else 56)
            val expectedPhSize = if (elfClass == ELFCLASS32) ELF32_PROGRAM_HEADER_SIZE else ELF64_PROGRAM_HEADER_SIZE
            require(ehsize >= headerSize && ehsize <= length) { "Invalid ELF header size." }
            require(phentsize >= expectedPhSize) { "Invalid program-header entry size." }
            require(phnum > 0) { "ELF has no program headers." }
            requireRange(phoff, phentsize.toLong() * phnum, length, "Program-header table")

            val loads = mutableListOf<LoadSegment>()
            val dynamics = mutableListOf<DynamicSegment>()
            repeat(phnum) { index ->
                val entryOffset = checkedAdd(phoff, phentsize.toLong() * index)
                val entry = buffer(readBytes(file, entryOffset, expectedPhSize, length), order)
                if (elfClass == ELFCLASS32) {
                    val type = entry.u32(0)
                    val offset = entry.u32(4)
                    val vaddr = entry.u32(8)
                    val filesz = entry.u32(16)
                    if (type == PT_LOAD.toLong()) loads += LoadSegment(offset, vaddr, filesz)
                    if (type == PT_DYNAMIC.toLong()) dynamics += DynamicSegment(offset, filesz)
                } else {
                    val type = entry.u32(0)
                    val offset = entry.u64(8)
                    val vaddr = entry.u64(16)
                    val filesz = entry.u64(32)
                    if (type == PT_LOAD.toLong()) loads += LoadSegment(offset, vaddr, filesz)
                    if (type == PT_DYNAMIC.toLong()) dynamics += DynamicSegment(offset, filesz)
                }
            }
            require(dynamics.size == 1) {
                if (dynamics.isEmpty()) "ELF has no PT_DYNAMIC segment."
                else "ELF has multiple PT_DYNAMIC segments."
            }
            val dynamic = dynamics.single()
            requireRange(dynamic.offset, dynamic.fileSize, length, "Dynamic segment")
            val entrySize = if (elfClass == ELFCLASS32) ELF32_DYNAMIC_ENTRY_SIZE else ELF64_DYNAMIC_ENTRY_SIZE
            require(dynamic.fileSize >= entrySize && dynamic.fileSize % entrySize == 0L) {
                "Invalid dynamic-segment size."
            }

            var stringTableAddress: Long? = null
            var stringTableSize: Long? = null
            val neededOffsets = mutableListOf<Long>()
            val rpathOffsets = mutableListOf<Long>()
            val runpathOffsets = mutableListOf<Long>()
            var terminated = false
            var cursor = 0L
            while (cursor < dynamic.fileSize) {
                val entryOffset = checkedAdd(dynamic.offset, cursor)
                val entry = buffer(readBytes(file, entryOffset, entrySize.toInt(), length), order)
                val tag = if (elfClass == ELFCLASS32) entry.i32(0).toLong() else entry.i64(0)
                val value = if (elfClass == ELFCLASS32) entry.u32(4) else entry.u64(8)
                cursor += entrySize
                when (tag) {
                    DT_NULL -> {
                        terminated = true
                        break
                    }
                    DT_NEEDED -> neededOffsets += value
                    DT_RPATH -> {
                        require(rpathOffsets.isEmpty()) { "Duplicate DT_RPATH entry." }
                        rpathOffsets += value
                    }
                    DT_RUNPATH -> {
                        require(runpathOffsets.isEmpty()) { "Duplicate DT_RUNPATH entry." }
                        runpathOffsets += value
                    }
                    DT_STRTAB -> {
                        require(stringTableAddress == null) { "Duplicate DT_STRTAB entry." }
                        stringTableAddress = value
                    }
                    DT_STRSZ -> {
                        require(stringTableSize == null) { "Duplicate DT_STRSZ entry." }
                        stringTableSize = value
                    }
                }
            }
            require(terminated) { "Dynamic segment has no DT_NULL terminator." }
            if (neededOffsets.isEmpty() && rpathOffsets.isEmpty() && runpathOffsets.isEmpty()) {
                return JvmElfDynamicMetadata(emptyList())
            }

            val strAddress = stringTableAddress
                ?: throw IllegalArgumentException("Dynamic string references exist without DT_STRTAB.")
            val strSize = stringTableSize
                ?: throw IllegalArgumentException("Dynamic string references exist without DT_STRSZ.")
            require(strSize > 0) { "ELF dynamic string table is empty." }
            val strOffset = virtualAddressToFileOffset(strAddress, strSize, loads, length)
            fun readDynamicStrings(offsets: List<Long>): List<String> = offsets.map { stringOffset ->
                require(stringOffset >= 0 && stringOffset < strSize) {
                    "Dynamic string offset is outside DT_STRTAB."
                }
                readString(file, checkedAdd(strOffset, stringOffset), strSize - stringOffset, length)
            }
            return JvmElfDynamicMetadata(
                neededLibraries = readDynamicStrings(neededOffsets),
                rpathEntries = readDynamicStrings(rpathOffsets),
                runpathEntries = readDynamicStrings(runpathOffsets)
            )
        }
    }

    private data class LoadSegment(val offset: Long, val virtualAddress: Long, val fileSize: Long)
    private data class DynamicSegment(val offset: Long, val fileSize: Long)

    private fun virtualAddressToFileOffset(
        address: Long,
        size: Long,
        loads: List<LoadSegment>,
        fileLength: Long
    ): Long {
        val matches = loads.mapNotNull { segment ->
            if (address < segment.virtualAddress) return@mapNotNull null
            val delta = address - segment.virtualAddress
            if (delta > segment.fileSize || size > segment.fileSize - delta) return@mapNotNull null
            checkedAdd(segment.offset, delta)
        }.distinct()
        require(matches.size == 1) { "DT_STRTAB does not map to exactly one file-backed PT_LOAD segment." }
        val offset = matches.single()
        requireRange(offset, size, fileLength, "Dynamic string table")
        return offset
    }

    private fun readString(
        file: RandomAccessFile,
        offset: Long,
        maxLength: Long,
        fileLength: Long
    ): String {
        require(maxLength > 0) { "Empty DT_NEEDED string." }
        val limit = minOf(maxLength, fileLength - offset)
        require(limit > 0) { "DT_NEEDED string starts outside the file." }
        val bytes = ArrayList<Byte>()
        var index = 0L
        while (index < limit) {
            file.seek(checkedAdd(offset, index))
            val value = file.read()
            if (value == -1) break
            if (value == 0) {
                require(bytes.isNotEmpty()) { "DT_NEEDED contains an empty name." }
                val raw = ByteArray(bytes.size) { bytes[it] }
                return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw)).toString()
            }
            bytes += value.toByte()
            index++
        }
        throw IllegalArgumentException("DT_NEEDED string is not NUL-terminated within DT_STRTAB.")
    }

    private fun readBytes(file: RandomAccessFile, offset: Long, size: Int, fileLength: Long): ByteArray {
        requireRange(offset, size.toLong(), fileLength, "ELF data")
        val result = ByteArray(size)
        file.seek(offset)
        file.readFully(result)
        return result
    }

    private fun requireRange(offset: Long, size: Long, fileLength: Long, label: String) {
        require(offset >= 0 && size >= 0 && offset <= fileLength && size <= fileLength - offset) {
            "$label is outside the ELF file."
        }
    }

    private fun checkedAdd(left: Long, right: Long): Long {
        require(left >= 0 && right >= 0 && left <= Long.MAX_VALUE - right) {
            "ELF offset arithmetic overflow."
        }
        return left + right
    }

    private fun buffer(bytes: ByteArray, order: ByteOrder): ByteBuffer =
        ByteBuffer.wrap(bytes).order(order)

    private fun ByteBuffer.u16(offset: Int): Int = getShort(offset).toInt() and 0xffff
    private fun ByteBuffer.u32(offset: Int): Long = getInt(offset).toLong() and 0xffff_ffffL
    private fun ByteBuffer.u64(offset: Int): Long {
        val value = getLong(offset)
        require(value >= 0) { "ELF 64-bit value exceeds supported file-offset range." }
        return value
    }

    private fun ByteBuffer.i32(offset: Int): Int = getInt(offset)
    private fun ByteBuffer.i64(offset: Int): Long = getLong(offset)

    companion object {
        private const val ELF_IDENT_SIZE = 16
        private const val ELF32_HEADER_SIZE = 52
        private const val ELF64_HEADER_SIZE = 64
        private const val ELF32_PROGRAM_HEADER_SIZE = 32
        private const val ELF64_PROGRAM_HEADER_SIZE = 56
        private const val ELF32_DYNAMIC_ENTRY_SIZE = 8L
        private const val ELF64_DYNAMIC_ENTRY_SIZE = 16L
        private const val ELFCLASS32 = 1
        private const val ELFCLASS64 = 2
        private const val ELFDATA2LSB = 1
        private const val ELFDATA2MSB = 2
        private const val EV_CURRENT = 1
        private const val PT_LOAD = 1
        private const val PT_DYNAMIC = 2
        private const val DT_NULL = 0L
        private const val DT_NEEDED = 1L
        private const val DT_STRTAB = 5L
        private const val DT_STRSZ = 10L
        private const val DT_RPATH = 15L
        private const val DT_RUNPATH = 29L
    }
}
