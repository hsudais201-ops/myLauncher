package com.voidcraft.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureRegistryPolicyTest {
    @Test
    fun catalogIdsAreUniqueAndNonBlank() {
        val catalog = FeatureRegistry.catalog()
        assertEquals(catalog.size, catalog.map { it.id }.toSet().size)
        catalog.forEach {
            assertNotNull(FeatureRegistry.find(it.id))
            assertTrue(it.id.isNotBlank())
            assertTrue(it.name.isNotBlank())
        }
    }

    @Test
    fun expandedStoreFeaturesRemainVisibleInCatalog() {
        val ids = FeatureRegistry.catalog().map { it.id }.toSet()
        val expected = setOf(
            "resource-pack-manager",
            "resource-pack-compatibility",
            "world-details-viewer",
            "local-playtime-tracker",
            "accessibility-presets",
            "profile-icon-studio",
            "configuration-backup",
            "network-diagnostics",
            "release-watch",
            "controller-presets",
            "resource-usage-history",
            "instance-notes"
        )
        assertTrue(ids.containsAll(expected))
        assertEquals(23, ids.size)
    }

    @Test
    fun onlyImplementedBuiltInsCanInstall() {
        val implemented = setOf("startup-diagnostics", "instance-notes", "network-diagnostics")
        FeatureRegistry.catalog().forEach { feature ->
            if (feature.id in implemented) {
                assertEquals(FeatureRegistry.Status.AVAILABLE, feature.status)
                assertTrue(FeatureRegistry.canInstall(feature.id))
            } else {
                assertFalse("Unexpected installable feature: ${feature.id}",
                    FeatureRegistry.canInstall(feature.id))
            }
        }
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
