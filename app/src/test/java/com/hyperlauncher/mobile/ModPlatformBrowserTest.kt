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
    fun rejectsUnknownCurseForgeLoader() {
        assertThrows(IllegalArgumentException::class.java) {
            ModPlatformBrowser.curseForgeLoaderType("unknown")
        }
    }
}
