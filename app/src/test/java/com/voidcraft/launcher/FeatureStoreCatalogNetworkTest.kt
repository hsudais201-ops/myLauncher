package com.voidcraft.launcher

import java.net.URI
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
}
