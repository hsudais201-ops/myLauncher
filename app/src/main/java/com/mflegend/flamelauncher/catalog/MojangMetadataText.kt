package com.mflegend.flamelauncher.catalog

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Strict UTF-8 decoding for downloaded Mojang JSON metadata. */
internal object MojangMetadataText {
    fun decode(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
}
