package com.flame.launcher.runtime

import java.io.File
import java.io.FileInputStream

/**
 * Validates the structural ELF header of extracted Android native shared libraries.
 *
 * This checks ELF class, byte order, header sizes, ET_DYN type, version and e_machine.
 * It does not prove DT_NEEDED dependencies resolve, symbols exist, or dlopen/JNI
 * initialization succeeds.
 */
class MinecraftNativeElfValidator {
    fun validate(files: List<File>, deviceArchitecture: String): MinecraftNativeElfValidation {
        val target = architecture(deviceArchitecture)
            ?: return MinecraftNativeElfValidation.UnsupportedArchitecture(deviceArchitecture)

        val invalid = ArrayList<String>()
        val inspected = ArrayList<String>()
        for (file in files) {
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Native ELF validation was cancelled.")
            }
            if (!file.name.endsWith(".so", ignoreCase = true)) continue
            inspected += file.path
            val header = readHeader(file)
            if (header == null || header.machine != target.machine || header.elfClass != target.elfClass) {
                invalid += file.path
            }
        }
        return if (invalid.isEmpty()) {
            MinecraftNativeElfValidation.Compatible(inspected.size, target.name)
        } else {
            MinecraftNativeElfValidation.Incompatible(invalid, target.name)
        }
    }

    private fun readHeader(file: File): ElfHeader? {
        return try {
            if (!file.isFile || file.length() < ELF_IDENT_SIZE) return null
            val ident = ByteArray(ELF_IDENT_SIZE)
            FileInputStream(file).use { input -> readFully(input, ident) }
            if (ident[0] != 0x7f.toByte() || ident[1] != 'E'.code.toByte() ||
                ident[2] != 'L'.code.toByte() || ident[3] != 'F'.code.toByte()
            ) return null

            val elfClass = ident[4].toInt() and 0xff
            val encoding = ident[5].toInt() and 0xff
            if (elfClass !in ELFCLASS32..ELFCLASS64 ||
                encoding !in ELF_DATA_LSB..ELF_DATA_MSB ||
                (ident[6].toInt() and 0xff) != EV_CURRENT
            ) return null

            val expectedSize = if (elfClass == ELFCLASS32) ELF32_HEADER_SIZE else ELF64_HEADER_SIZE
            if (file.length() < expectedSize) return null
            val header = ByteArray(expectedSize)
            FileInputStream(file).use { input -> readFully(input, header) }

            val littleEndian = encoding == ELF_DATA_LSB
            val type = unsigned16(header, 16, littleEndian)
            val machine = unsigned16(header, 18, littleEndian)
            val version = unsigned32(header, 20, littleEndian)
            val headerSizeOffset = if (elfClass == ELFCLASS32) 40 else 52
            val declaredHeaderSize = unsigned16(header, headerSizeOffset, littleEndian)

            if (type != ET_DYN || version != EV_CURRENT.toLong() || declaredHeaderSize != expectedSize) {
                return null
            }
            ElfHeader(elfClass, machine)
        } catch (_: Exception) {
            null
        }
    }

    private fun readFully(input: FileInputStream, bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Native ELF validation was cancelled.")
            }
            val count = input.read(bytes, offset, bytes.size - offset)
            if (count < 0) throw java.io.EOFException("Truncated ELF header.")
            if (count > 0) offset += count
        }
    }

    private fun unsigned16(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int {
        val first = bytes[offset].toInt() and 0xff
        val second = bytes[offset + 1].toInt() and 0xff
        return if (littleEndian) first or (second shl 8) else (first shl 8) or second
    }

    private fun unsigned32(bytes: ByteArray, offset: Int, littleEndian: Boolean): Long {
        val values = (0..3).map { bytes[offset + it].toLong() and 0xffL }
        return if (littleEndian) {
            values[0] or (values[1] shl 8) or (values[2] shl 16) or (values[3] shl 24)
        } else {
            (values[0] shl 24) or (values[1] shl 16) or (values[2] shl 8) or values[3]
        }
    }

    private fun architecture(raw: String): Target? = when (raw.trim().lowercase()) {
        "aarch64", "arm64", "arm64-v8a" -> Target("arm64", ELFCLASS64, EM_AARCH64)
        "arm", "armv7", "armv7l", "armeabi-v7a" -> Target("arm", ELFCLASS32, EM_ARM)
        "x86_64", "amd64" -> Target("x86_64", ELFCLASS64, EM_X86_64)
        "x86", "i386", "i486", "i586", "i686" -> Target("x86", ELFCLASS32, EM_386)
        else -> null
    }

    private data class Target(val name: String, val elfClass: Int, val machine: Int)
    private data class ElfHeader(val elfClass: Int, val machine: Int)

    companion object {
        private const val ELF_IDENT_SIZE = 16
        private const val ELF32_HEADER_SIZE = 52
        private const val ELF64_HEADER_SIZE = 64
        private const val ELFCLASS32 = 1
        private const val ELFCLASS64 = 2
        private const val ELF_DATA_LSB = 1
        private const val ELF_DATA_MSB = 2
        private const val ET_DYN = 3
        private const val EV_CURRENT = 1
        private const val EM_386 = 3
        private const val EM_ARM = 40
        private const val EM_X86_64 = 62
        private const val EM_AARCH64 = 183
    }
}

sealed interface MinecraftNativeElfValidation {
    data class Compatible(val inspectedLibraryCount: Int, val architecture: String) : MinecraftNativeElfValidation
    data class Incompatible(val files: List<String>, val architecture: String) : MinecraftNativeElfValidation
    data class UnsupportedArchitecture(val architecture: String) : MinecraftNativeElfValidation
}
