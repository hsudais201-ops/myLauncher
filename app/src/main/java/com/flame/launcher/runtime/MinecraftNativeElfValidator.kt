package com.flame.launcher.runtime

import java.io.File
import java.io.FileInputStream

/**
 * Validates ELF headers for extracted Android native shared libraries.
 *
 * This checks the ELF class and e_machine field only. It does not prove that
 * DT_NEEDED dependencies resolve, symbols exist, or dlopen/JNI initialization
 * will succeed.
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
            if (!file.isFile || file.length() < ELF_HEADER_SIZE) return null
            val bytes = ByteArray(ELF_HEADER_SIZE)
            FileInputStream(file).use { input ->
                var offset = 0
                while (offset < bytes.size) {
                    val count = input.read(bytes, offset, bytes.size - offset)
                    if (count < 0) return null
                    if (count == 0) continue
                    offset += count
                }
            }
            if (bytes[0] != 0x7f.toByte() || bytes[1] != 'E'.code.toByte() ||
                bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()
            ) return null
            val elfClass = bytes[4].toInt() and 0xff
            val dataEncoding = bytes[5].toInt() and 0xff
            if (elfClass !in 1..2 || dataEncoding !in 1..2 || (bytes[6].toInt() and 0xff) != 1) return null
            val first = bytes[18].toInt() and 0xff
            val second = bytes[19].toInt() and 0xff
            val machine = if (dataEncoding == ELF_DATA_LSB) first or (second shl 8)
                else (first shl 8) or second
            ElfHeader(elfClass, machine)
        } catch (_: Exception) {
            null
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
        private const val ELF_HEADER_SIZE = 20
        private const val ELFCLASS32 = 1
        private const val ELFCLASS64 = 2
        private const val ELF_DATA_LSB = 1
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
