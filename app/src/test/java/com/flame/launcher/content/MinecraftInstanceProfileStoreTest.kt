package com.flame.launcher.content

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftInstanceProfileStoreTest {
    @Test
    fun createsIsolatedProfileAndReturnsCorrectInstallTarget() {
        val root = createTempDir(prefix = "flame-profiles-")
        try {
            val store = MinecraftInstanceProfileStore(File(root, "profiles"))
            val result = store.createProfile("Survival", "1.20.1", MinecraftLoader.FABRIC)
            assertTrue(result is MinecraftInstanceProfileResult.Created)
            val profile = (result as MinecraftInstanceProfileResult.Created).profile
            assertTrue(profile.instanceDirectory.isDirectory)
            assertTrue(profile.contentDirectory.isDirectory)
            assertEquals(
                profile.contentDirectory.absolutePath,
                profile.target().contentDirectory
            )
            assertEquals("1.20.1", profile.target().minecraftVersion)
            assertEquals(MinecraftLoader.FABRIC, profile.target().loader)
            assertFalse(File(root, "profiles/other-instance").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun selectedProfilePersistsAcrossStoreInstances() {
        val root = createTempDir(prefix = "flame-profiles-")
        try {
            val profilesRoot = File(root, "profiles")
            val firstStore = MinecraftInstanceProfileStore(profilesRoot)
            val created = firstStore.createProfile("Creative", "1.21", MinecraftLoader.NEOFORGE)
                as MinecraftInstanceProfileResult.Created
            val selected = firstStore.selectProfile(created.profile.id)
            assertTrue(selected is MinecraftInstanceProfileResult.Selected)

            val restored = MinecraftInstanceProfileStore(profilesRoot).selectedProfile()
            assertTrue(restored is MinecraftInstanceProfileResult.Selected)
            assertEquals(created.profile.id, (restored as MinecraftInstanceProfileResult.Selected).profile.id)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsInvalidProfileInputs() {
        val root = createTempDir(prefix = "flame-profiles-")
        try {
            val store = MinecraftInstanceProfileStore(File(root, "profiles"))
            assertTrue(store.createProfile("../escape", "1.20.1", MinecraftLoader.FABRIC) is MinecraftInstanceProfileResult.Created)
            assertTrue(store.createProfile("Invalid version", "latest", MinecraftLoader.FABRIC) is MinecraftInstanceProfileResult.Failed)
            assertTrue(store.createProfile("Unknown loader", "1.20.1", MinecraftLoader.UNKNOWN) is MinecraftInstanceProfileResult.Failed)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun emptyStoreHasNoSelectedProfile() {
        val root = createTempDir(prefix = "flame-profiles-")
        try {
            val store = MinecraftInstanceProfileStore(File(root, "profiles"))
            assertTrue(store.listProfiles() is MinecraftInstanceProfileResult.Profiles)
            assertTrue(store.selectedProfile() is MinecraftInstanceProfileResult.Failed)
        } finally {
            root.deleteRecursively()
        }
    }
}
