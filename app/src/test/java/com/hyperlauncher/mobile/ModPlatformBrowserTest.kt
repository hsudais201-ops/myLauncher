package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ModPlatformBrowserTest {
    @Test
    fun mapsCurseForgeLoaderTypes() {
        assertNull(ModPlatformBrowser.curseForgeLoaderType("any"))
        assertEquals(1, ModPlatformBrowser.curseForgeLoaderType("forge"))
        assertEquals(4, ModPlatformBrowser.curseForgeLoaderType("fabric"))
        assertEquals(5, ModPlatformBrowser.curseForgeLoaderType("quilt"))
        assertEquals(6, ModPlatformBrowser.curseForgeLoaderType("neoforge"))
    }

    @Test
    fun mapsCurseForgeSortFieldsToDocumentedValues() {
        assertEquals(6, ModPlatformBrowser.curseForgeSortField("downloads"))
        assertEquals(3, ModPlatformBrowser.curseForgeSortField("updated"))
        assertEquals(2, ModPlatformBrowser.curseForgeSortField("relevance"))
    }

    @Test
    fun matchesCurseForgeClassesWithoutConfusingModsAndModpacks() {
        assertEquals(true, ModPlatformBrowser.matchesCurseForgeClass("mod", "Mods"))
        assertEquals(false, ModPlatformBrowser.matchesCurseForgeClass("mod", "Modpacks"))
        assertEquals(true, ModPlatformBrowser.matchesCurseForgeClass("modpack", "Modpacks"))
        assertEquals(true, ModPlatformBrowser.matchesCurseForgeClass("resourcepack", "Texture Packs"))
    }

    @Test
    fun recognizesCurseForgeDataPackClass() {
        assertEquals(true, ModPlatformBrowser.matchesCurseForgeClass("datapack", "Data Packs"))
        assertEquals(false, ModPlatformBrowser.matchesCurseForgeClass("mod", "Data Packs"))
    }

    @Test
    fun acceptsOnlyOfficialModDownloadHosts() {
        assertEquals(true, ModPlatformBrowser.isTrustedModDownloadUrl("https://cdn.modrinth.com/data/project/file.jar"))
        assertEquals(true, ModPlatformBrowser.isTrustedModDownloadUrl("https://edge.forgecdn.net/files/123/file.jar"))
        assertEquals(false, ModPlatformBrowser.isTrustedModDownloadUrl("http://cdn.modrinth.com/data/project/file.jar"))
        assertEquals(false, ModPlatformBrowser.isTrustedModDownloadUrl("https://evil.example/file.jar"))
        assertEquals(false, ModPlatformBrowser.isTrustedModDownloadUrl("https://cdn.modrinth.com.evil.example/file.jar"))
    }

    @Test
    fun rejectsUnknownCurseForgeLoader() {
        assertThrows(IllegalArgumentException::class.java) {
            ModPlatformBrowser.curseForgeLoaderType("unknown")
        }
    }
}
