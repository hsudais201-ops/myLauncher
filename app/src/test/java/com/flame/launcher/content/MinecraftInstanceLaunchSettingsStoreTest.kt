package com.flame.launcher.content

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftInstanceLaunchSettingsStoreTest {
    @Test
    fun savesAndReloadsSettingsForOnlyTheTargetProfile() {
        val root = Files.createTempDirectory("flame-launch-settings-").toFile()
        try {
            val profilesRoot = File(root, "profiles")
            val profiles = MinecraftInstanceProfileStore(profilesRoot)
            val first = (profiles.createProfile("Main", "1.21", MinecraftLoader.FABRIC)
                as MinecraftInstanceProfileResult.Created).profile
            val second = (profiles.createProfile("Test", "1.20.1", MinecraftLoader.QUILT)
                as MinecraftInstanceProfileResult.Created).profile
            val settings = MinecraftInstanceLaunchSettings(
                minimumMemoryMb = 1024,
                maximumMemoryMb = 3072,
                width = 1920,
                height = 1080,
                fullscreen = true,
                jvmArguments = "-XX:+UseG1GC",
                gameArguments = "--demo"
            )
            val store = MinecraftInstanceLaunchSettingsStore(profilesRoot)
            assertTrue(store.save(first, settings) is MinecraftInstanceLaunchSettingsResult.Saved)
            assertEquals(settings, (store.load(first) as MinecraftInstanceLaunchSettingsResult.Loaded).settings)
            assertEquals(
                MinecraftInstanceLaunchSettings(),
                (store.load(second) as MinecraftInstanceLaunchSettingsResult.Loaded).settings
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsInvalidMemoryResolutionAndControlCharacters() {
        assertTrue(MinecraftInstanceLaunchSettings(maximumMemoryMb = 256).validationError() != null)
        assertTrue(MinecraftInstanceLaunchSettings(minimumMemoryMb = 3072, maximumMemoryMb = 2048).validationError() != null)
        assertTrue(MinecraftInstanceLaunchSettings(width = 320).validationError() != null)
        assertTrue(MinecraftInstanceLaunchSettings(jvmArguments = "-Xmx1G\nmalicious").validationError() != null)
    }

    @Test
    fun refusesSettingsForForgedProfilePath() {
        val root = Files.createTempDirectory("flame-launch-settings-").toFile()
        try {
            val profilesRoot = File(root, "profiles")
            val profiles = MinecraftInstanceProfileStore(profilesRoot)
            val profile = (profiles.createProfile("Main", "1.21", MinecraftLoader.FABRIC)
                as MinecraftInstanceProfileResult.Created).profile
            val forged = profile.copy(instanceDirectory = root, contentDirectory = root)
            assertTrue(
                MinecraftInstanceLaunchSettingsStore(profilesRoot).load(forged)
                    is MinecraftInstanceLaunchSettingsResult.Failed
            )
        } finally {
            root.deleteRecursively()
        }
    }
    @Test
    fun rejectsUnknownSettingsSchemaAndDoesNotSilentlyApplyDefaults() {
        val root = Files.createTempDirectory("flame-launch-settings-schema-").toFile()
        try {
            val profilesRoot = File(root, "profiles")
            val profiles = MinecraftInstanceProfileStore(profilesRoot)
            val profile = (profiles.createProfile("Main", "1.21", MinecraftLoader.FABRIC)
                as MinecraftInstanceProfileResult.Created).profile
            File(profile.instanceDirectory, "launch-settings.properties").writeText(
                "schema=99\\nminimumMemoryMb=512\\nmaximumMemoryMb=1536\\n"
            )

            val result = MinecraftInstanceLaunchSettingsStore(profilesRoot).load(profile)
            assertTrue(result is MinecraftInstanceLaunchSettingsResult.Failed)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun recommendsConservativeHeapForFourGigabyteAndSmallerDevices() {
        assertEquals(1536, MinecraftInstanceLaunchSettings.recommendedMaximumMemoryMb(4096))
        assertEquals(768, MinecraftInstanceLaunchSettings.recommendedMaximumMemoryMb(2048))
        assertEquals(2048, MinecraftInstanceLaunchSettings.recommendedMaximumMemoryMb(8192))
        assertEquals(1536, MinecraftInstanceLaunchSettings.recommendedMaximumMemoryMb(null))
    }

}
