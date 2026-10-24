package com.voidcraft.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File
import java.io.IOException

class InstanceCatalogRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun upsertAndReloadRoundTripMetadata() {
        val directory = temporaryFolder.newFolder("catalog")
        val repository = FileInstanceCatalogRepository(directory)
        val profile = InstanceProfile(
            id = "survival_1",
            name = "Survival World",
            minecraftVersion = "1.21.4",
            loaderId = "fabric",
            createdAtEpochMillis = 1234L
        )

        repository.upsert(profile)

        assertEquals(profile, FileInstanceCatalogRepository(directory).get("survival_1"))
        assertEquals(listOf(profile), repository.list())
        assertEquals("instances/survival_1/game", profile.relativeGameDirectory)
    }

    @Test
    fun upsertReplacesSameIdInsteadOfAddingDuplicate() {
        val repository = FileInstanceCatalogRepository(temporaryFolder.newFolder("catalog"))
        repository.upsert(profile(name = "Old name"))
        repository.upsert(profile(name = "New name"))

        assertEquals(1, repository.list().size)
        assertEquals("New name", repository.get("test_1")?.name)
    }

    @Test
    fun removeOnlyRemovesCatalogEntryAndPreservesDirectoryContents() {
        val appData = temporaryFolder.newFolder("app-data")
        val gameData = File(appData, "instances/test_1/game/saves/world")
        assertTrue(gameData.mkdirs())
        val sentinel = File(gameData, "keep-me.dat")
        sentinel.writeText("preserve this file")

        val repository = FileInstanceCatalogRepository(appData)
        repository.upsert(profile())
        assertTrue(repository.remove("test_1"))

        assertTrue("World file must remain untouched", sentinel.exists())
        assertEquals("preserve this file", sentinel.readText())
        assertTrue(repository.list().isEmpty())
        assertFalse(repository.remove("test_1"))
    }

    @Test
    fun invalidIdsCannotAddressFilesystemPaths() {
        val repository = FileInstanceCatalogRepository(temporaryFolder.newFolder("catalog"))
        assertNull(repository.get("../outside"))
        assertFalse(repository.remove("../outside"))
    }

    @Test
    fun unknownSchemaFailsClosedInsteadOfReturningEmptyCatalog() {
        val directory = temporaryFolder.newFolder("catalog")
        val catalog = File(directory, "instances.properties")
        catalog.writeText("schema.version=999\ninstance.count=0\n")
        val repository = FileInstanceCatalogRepository(directory)

        try {
            repository.list()
            throw AssertionError("Expected unsupported schema to fail")
        } catch (_: IOException) {
            // Expected: callers must not overwrite an unknown catalog format.
        }
        assertTrue(catalog.exists())
        assertEquals("schema.version=999\ninstance.count=0\n", catalog.readText())
    }

    private fun profile(name: String = "Test profile") = InstanceProfile(
        id = "test_1",
        name = name,
        minecraftVersion = "1.20.4",
        loaderId = "vanilla",
        createdAtEpochMillis = 100L
    )
}
