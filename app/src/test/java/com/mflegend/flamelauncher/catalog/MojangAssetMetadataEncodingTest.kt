package com.mflegend.flamelauncher.catalog

import java.nio.charset.CharacterCodingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MojangAssetMetadataEncodingTest {
    @Test
    fun decodesValidUtf8AssetMetadata() {
        assertEquals("{\"objects\":{}}", MojangMetadataText.decode("{\"objects\":{}}".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun rejectsMalformedUtf8AssetMetadata() {
        val error = runCatching { MojangMetadataText.decode(byteArrayOf(0x7b, 0x22, 0xc3.toByte(), 0x28, 0x22, 0x7d)) }
            .exceptionOrNull()
        assertTrue(error is CharacterCodingException)
    }
}
