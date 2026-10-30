package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class VerifiedMinecraftFileDownloaderTest {
    @Test
    fun resolvesOnlyPathsInsideLauncherStorage() {
        val root = File(System.getProperty("java.io.tmpdir"), "hyper-launcher-test")
        val target = VerifiedMinecraftFileDownloader.resolveTarget(root, "libraries/example/library.jar")
        assertTrue(target.path.startsWith(root.canonicalPath + File.separator))
        assertEquals("library.jar", target.name)
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        val root = File(System.getProperty("java.io.tmpdir"), "hyper-launcher-test")
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedMinecraftFileDownloader.resolveTarget(root, "../outside.jar")
        }
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedMinecraftFileDownloader.resolveTarget(root, "/outside.jar")
        }
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedMinecraftFileDownloader.resolveTarget(root, "libraries/../outside.jar")
        }
    }

    @Test
    fun onlyAcceptsHttpsDownloadsFromAllowlistedHosts() {
        assertTrue(VerifiedMinecraftFileDownloader.isTrustedUrl("https://piston-data.mojang.com/client.jar"))
        assertTrue(VerifiedMinecraftFileDownloader.isTrustedUrl("https://libraries.minecraft.net/library.jar"))
        assertFalse(VerifiedMinecraftFileDownloader.isTrustedUrl("http://piston-data.mojang.com/client.jar"))
        assertFalse(VerifiedMinecraftFileDownloader.isTrustedUrl("https://evil.example/client.jar"))
    }
}
