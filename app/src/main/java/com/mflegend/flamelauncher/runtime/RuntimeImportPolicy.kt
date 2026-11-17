package com.mflegend.flamelauncher.runtime

import java.io.File

/** Path and capacity limits for copying a user-selected runtime into private storage. */
internal object RuntimeImportPolicy {
    const val MAX_ENTRIES = 30_000
    const val MAX_FILE_BYTES = 1024L * 1024L * 1024L
    const val MAX_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L
    const val MAX_PATH_DEPTH = 32

    fun validateEntryName(name: String) {
        require(
            name.isNotBlank() &&
                name.length <= 255 &&
                name != "." &&
                name != ".." &&
                '/' !in name &&
                '\\' !in name &&
                name.indexOf(0.toChar()) < 0 &&
                name.matches(Regex("[A-Za-z0-9._+@-]+"))
        ) { "Java runtime contains an unsafe file or folder name." }
    }

    fun validateRelativePath(segments: List<String>) {
        require(segments.isNotEmpty() && segments.size <= MAX_PATH_DEPTH) {
            "Java runtime path exceeds the allowed directory depth."
        }
        segments.forEach(::validateEntryName)
    }

    fun isContained(parent: File, child: File): Boolean = try {
        val canonicalParent = parent.canonicalFile
        val canonicalChild = child.canonicalFile
        canonicalChild != canonicalParent && canonicalChild.toPath().startsWith(canonicalParent.toPath())
    } catch (_: Exception) {
        false
    }
}

/** Confirms an imported libjvm is at least an AArch64 ELF64 binary, not a symlink-text placeholder. */
internal object RuntimeBinaryPolicy {
    private const val ELF_HEADER_BYTES = 20
    private const val ELF_CLASS_64 = 2
    private const val ELF_DATA_LITTLE_ENDIAN = 1
    private const val EM_AARCH64 = 183

    fun isArm64Elf64(header: ByteArray): Boolean {
        if (header.size < ELF_HEADER_BYTES) return false
        if (header[0] != 0x7f.toByte() || header[1] != 'E'.code.toByte() ||
            header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()
        ) return false
        if (header[4].toInt() and 0xff != ELF_CLASS_64) return false
        if (header[5].toInt() and 0xff != ELF_DATA_LITTLE_ENDIAN) return false
        val machine = (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)
        return machine == EM_AARCH64
    }

    fun isArm64Elf64(file: File): Boolean {
        return try {
            if (!file.isFile || file.length() < ELF_HEADER_BYTES) {
                false
            } else {
                val header = ByteArray(ELF_HEADER_BYTES)
                var complete = true
                file.inputStream().use { input ->
                    var offset = 0
                    while (offset < header.size) {
                        val count = input.read(header, offset, header.size - offset)
                        if (count < 0) {
                            complete = false
                            break
                        }
                        offset += count
                    }
                }
                complete && isArm64Elf64(header)
            }
        } catch (_: Exception) {
            false
        }
    }
}
