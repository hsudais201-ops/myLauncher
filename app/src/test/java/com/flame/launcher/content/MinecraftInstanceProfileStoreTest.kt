package com.flame.launcher.content

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftInstanceProfileStoreTest {
    @Test
    fun createsIsolatedProfileAndReturnsCorrectInstallTarget() {
        val root = Files.createTempDirectory("flame-profiles-").toFile()
        try {
            val store = MinecraftInstanceProfileStore(File(root, "profiles"))
            val result = store.createProfile("Survival", "1.20.1", MinecraftLoader.FABRIC)
            assertTrue(result is MinecraftInstanceProfileResult.Created)
            val profile = (result as MinecraftInstanceProfileResult.Created).profile
            assertTrue(profile.instanceDirectory.isDirectory)
            assertTrue(profile.contentDirectory.isDirectory)
            assertEquals(profile.contentDirectory.absolutePath, profile.target().contentDirectory)
            assertEquals("1.20.1", profile.target().minecraftVersion)
            assertEquals(MinecraftLoader.FABRIC, profile.target().loader)
            assertFalse(File(root, "profiles/other-instance").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun selectedProfilePersistsAcrossStoreInstances() {
        val root = Files.createTempDirectory("flame-profiles-").toFile()
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
        val root = Files.createTempDirectory("flame-profiles-").toFile()
        try {
            val store = MinecraftInstanceProfileStore(File(root, "profiles"))
            assertTrue(store.createProfile("../escape", "1.20.1", MinecraftLoader.FABRIC) is MinecraftInstanceProfileResult.Failed)
            assertTrue(store.createProfile("Invalid version", "latest", MinecraftLoader.FABRIC) is MinecraftInstanceProfileResult.Failed)
            assertTrue(store.createProfile("Unknown loader", "1.20.1", MinecraftLoader.UNKNOWN) is MinecraftInstanceProfileResult.Failed)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun emptyStoreHasNoSelectedProfile() {
        val root = Files.createTempDirectory("flame-profiles-").toFile()
        try {
            val store = MinecraftInstanceProfileStore(File(root, "profiles"))
            assertTrue(store.listProfiles() is MinecraftInstanceProfileResult.Profiles)
            assertTrue(store.selectedProfile() is MinecraftInstanceProfileResult.Failed)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun renamesProfileAndPersistsTheNewName() {
        val root = Files.createTempDirectory("flame-profiles-").toFile()
        try {
            val profilesRoot = File(root, "profiles")
            val store = MinecraftInstanceProfileStore(profilesRoot)
            val created = store.createProfile("Old name", "1.20.1", MinecraftLoader.FABRIC)
                as MinecraftInstanceProfileResult.Created

            val renamed = store.renameProfile(created.profile.id, "New name")
            assertTrue(renamed is MinecraftInstanceProfileResult.Updated)
            assertEquals("New name", (renamed as MinecraftInstanceProfileResult.Updated).profile.name)
            val restored = MinecraftInstanceProfileStore(profilesRoot).listProfiles()
                as MinecraftInstanceProfileResult.Profiles
            assertEquals("New name", restored.profiles.single().name)
            assertEquals(created.profile.id, restored.profiles.single().id)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deletingSelectedProfileClearsSelectionAndRemovesOnlyThatInstance() {
        val root = Files.createTempDirectory("flame-profiles-").toFile()
        try {
            val profilesRoot = File(root, "profiles")
            val store = MinecraftInstanceProfileStore(profilesRoot)
            val first = (store.createProfile("First", "1.20.1", MinecraftLoader.FABRIC)
                as MinecraftInstanceProfileResult.Created).profile
            val second = (store.createProfile("Second", "1.21", MinecraftLoader.QUILT)
                as MinecraftInstanceProfileResult.Created).profile
            assertTrue(store.selectProfile(first.id) is MinecraftInstanceProfileResult.Selected)

            val deleted = store.deleteProfile(first.id)
            assertTrue(deleted is MinecraftInstanceProfileResult.Deleted)
            assertFalse(first.instanceDirectory.exists())
            assertTrue(second.instanceDirectory.exists())
            assertTrue(store.selectedProfile() is MinecraftInstanceProfileResult.Failed)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun refusesProfileMutationForUnknownOrMalformedIds() {
        val root = Files.createTempDirectory("flame-profiles-").toFile()
        try {
            val store = MinecraftInstanceProfileStore(File(root, "profiles"))
            assertTrue(store.renameProfile("../escape", "Name") is MinecraftInstanceProfileResult.Failed)
            assertTrue(store.deleteProfile("../escape") is MinecraftInstanceProfileResult.Failed)
        } finally {
            root.deleteRecursively()
        }
    }

}
