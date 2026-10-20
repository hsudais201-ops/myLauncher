package com.voidcraft.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class FeatureRegistryPolicyTest {
    @Test
    fun catalogIdsAreUniqueAndNonBlank() {
        val catalog = FeatureRegistry.catalog()
        assertEquals(catalog.size, catalog.map { it.id }.toSet().size)
        catalog.forEach {
            assertNotNull(FeatureRegistry.find(it.id))
            assert(it.id.isNotBlank())
            assert(it.name.isNotBlank())
        }
    }

    @Test
    fun onlyImplementedBuiltInDiagnosticsCanInstall() {
        FeatureRegistry.catalog().forEach { feature ->
            if (feature.id == "startup-diagnostics") {
                assertEquals(FeatureRegistry.Status.AVAILABLE, feature.status)
                assert(!FeatureRegistry.canInstall("unknown-feature"))
            } else {
                assertFalse("Unexpected installable feature: ${feature.id}",
                    FeatureRegistry.canInstall(feature.id))
            }
        }
        assert(FeatureRegistry.canInstall("startup-diagnostics"))
        assertFalse(FeatureRegistry.canInstall("unknown-feature"))
        assertFalse(FeatureRegistry.canInstall("../unsafe-path"))
    }

    @Test
    fun communityUploadRequiresReview() {
        val upload = FeatureRegistry.find("community-feature-upload")
        assertNotNull(upload)
        assertEquals(FeatureRegistry.Status.REVIEW_REQUIRED, upload!!.status)
        assertFalse(FeatureRegistry.canInstall(upload.id))
    }
}
