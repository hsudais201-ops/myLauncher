package com.voidcraft.launcher

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureStorePackageRemovalTest {
    @Test
    fun removesOnlyTheSelectedFeaturePackageAndItsTemporaryFiles() {
        val directory = Files.createTempDirectory("voidcraft-feature-store").toFile()
        try {
            File(directory, "advanced-mod-manager.package").writeText("package")
            File(directory, "advanced-mod-manager.previous").writeText("previous")
            File(directory, "advanced-mod-manager.12345.part").writeText("partial")
            val unrelated = File(directory, "social-hub.package").also { it.writeText("keep") }

            assertTrue(removeFeaturePackageFiles(directory, "advanced-mod-manager"))
            assertFalse(File(directory, "advanced-mod-manager.package").exists())
            assertFalse(File(directory, "advanced-mod-manager.previous").exists())
            assertFalse(File(directory, "advanced-mod-manager.12345.part").exists())
            assertTrue(unrelated.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun succeedsWhenTheFeatureHasNoStoredPackage() {
        val directory = Files.createTempDirectory("voidcraft-feature-store-empty").toFile()
        try {
            assertTrue(removeFeaturePackageFiles(directory, "social-hub"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
