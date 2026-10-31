package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LauncherInstanceStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun createsDefaultInstanceWithoutMovingLegacyStorage() {
        val root = temporaryFolder.newFolder("minecraft")
        val store = LauncherInstanceStore(root)
        val state = store.list()

        assertEquals("default", state.getString("selectedId"))
        assertEquals(1, state.getJSONArray("instances").length())
        assertEquals(root.canonicalFile, store.storageRoot("default").canonicalFile)
    }

    @Test
    fun createsAndSelectsIndependentInstanceDirectories() {
        val root = temporaryFolder.newFolder("minecraft")
        val store = LauncherInstanceStore(root)
        val state = store.create("Fabric Test", "1.21.4")
        val id = state.getString("selectedId")
        val selectedRoot = store.storageRoot(id)

        assertNotEquals(root.canonicalFile, selectedRoot.canonicalFile)
        assertTrue(selectedRoot.isDirectory)
        assertEquals("Fabric Test", state.getJSONArray("instances").getJSONObject(1).getString("name"))
        assertEquals(id, LauncherInstanceStore(root).list().getString("selectedId"))
    }

    @Test
    fun selectingAnotherInstanceChangesActiveProfile() {
        val root = temporaryFolder.newFolder("minecraft")
        val store = LauncherInstanceStore(root)
        val created = store.create("Test profile", "snapshot")
        val id = created.getString("selectedId")

        val state = store.select("default")
        assertEquals("default", state.getString("selectedId"))
        assertEquals(root.canonicalFile, store.storageRoot("default").canonicalFile)
        assertNotEquals(root.canonicalFile, store.storageRoot(id).canonicalFile)
    }

    @Test
    fun selectedInstanceCanRememberItsVersion() {
        val store = LauncherInstanceStore(temporaryFolder.newFolder("minecraft"))
        val created = store.create("Release profile", "release")
        val id = created.getString("selectedId")
        val state = store.updateSelectedVersion("1.20.1")
        val instances = state.getJSONArray("instances")
        var savedVersion = ""
        for (i in 0 until instances.length()) {
            val item = instances.getJSONObject(i)
            if (item.getString("id") == id) savedVersion = item.getString("version")
        }
        assertEquals("1.20.1", savedVersion)
    }

    @Test
    fun preventsDuplicateAndUnsafeInstanceNames() {
        val store = LauncherInstanceStore(temporaryFolder.newFolder("minecraft"))
        store.create("My Instance", "release")
        assertThrows(IllegalArgumentException::class.java) { store.create("my instance", "release") }
        assertThrows(IllegalArgumentException::class.java) { store.create("../outside", "release") }
    }

    @Test
    fun onlyDeletesUnselectedNonDefaultInstances() {
        val root = temporaryFolder.newFolder("minecraft")
        val store = LauncherInstanceStore(root)
        val created = store.create("Temporary", "release")
        val id = created.getString("selectedId")
        assertThrows(IllegalArgumentException::class.java) { store.delete(id) }

        store.select("default")
        val afterDelete = store.delete(id)
        assertEquals(1, afterDelete.getJSONArray("instances").length())
        assertFalse(store.storageRoot(id).exists())
        assertThrows(IllegalArgumentException::class.java) { store.delete("default") }
    }
}
