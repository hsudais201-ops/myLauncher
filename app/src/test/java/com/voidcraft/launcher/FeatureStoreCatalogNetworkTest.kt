package com.voidcraft.launcher

import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureStoreCatalogNetworkTest {
    @Test
    fun acceptsOnlyApprovedGitHubHostsOverHttps() {
        assertTrue(isApprovedFeatureCatalogUri(URI("https://raw.githubusercontent.com/owner/repo/main/store/catalog.json")))
        assertTrue(isApprovedFeatureCatalogUri(URI("https://github.com/owner/repo/raw/main/store/catalog.json")))
        assertTrue(isApprovedFeatureCatalogUri(URI("https://raw.github.com/owner/repo/main/store/catalog.json")))

        assertFalse(isApprovedFeatureCatalogUri(URI("http://raw.githubusercontent.com/owner/repo/main/store/catalog.json")))
        assertFalse(isApprovedFeatureCatalogUri(URI("https://raw.githubusercontent.com.attacker.example/catalog.json")))
        assertFalse(isApprovedFeatureCatalogUri(URI("https://attacker.example/catalog.json")))
        assertFalse(isApprovedFeatureCatalogUri(URI("https://user@raw.githubusercontent.com/owner/repo/catalog.json")))
        assertFalse(isApprovedFeatureCatalogUri(URI("https://raw.githubusercontent.com:8443/owner/repo/catalog.json")))
    }

    @Test
    fun allowsUnknownLengthButRejectsEmptyNegativeAndOversizedCatalogs() {
        assertTrue(featureCatalogContentLengthAllowed(-1L))
        assertTrue(featureCatalogContentLengthAllowed(1L))
        assertTrue(featureCatalogContentLengthAllowed(1024L * 1024L))
        assertFalse(featureCatalogContentLengthAllowed(-2L))
        assertFalse(featureCatalogContentLengthAllowed(0L))
        assertFalse(featureCatalogContentLengthAllowed(1024L * 1024L + 1L))
    }

    @Test
    fun readsKnownAndUnknownLengthBodiesWithinTheLimit() {
        val bytes = """{"schemaVersion":1,"items":[]}""".toByteArray(StandardCharsets.UTF_8)
        assertArrayEquals(bytes, readBoundedFeatureCatalog(ByteArrayInputStream(bytes), bytes.size.toLong()))
        assertArrayEquals(bytes, readBoundedFeatureCatalog(ByteArrayInputStream(bytes), -1L))
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsOversizedChunkedCatalogEvenWithoutContentLength() {
        val oversized = ByteArray(1024 * 1024 + 1)
        readBoundedFeatureCatalog(ByteArrayInputStream(oversized), -1L)
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsDeclaredLengthMismatch() {
        val bytes = "short".toByteArray(StandardCharsets.UTF_8)
        readBoundedFeatureCatalog(ByteArrayInputStream(bytes), bytes.size.toLong() + 1L)
    }
}
