package com.hyperlauncher.mobile

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedAndroidRuntimeDownloaderTest {
    @Test
    fun acceptsOnlyGitHubReleaseAndAssetHostsOverHttps() {
        assertTrue(
            VerifiedAndroidRuntimeDownloader.isTrustedUrl(
                "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/download_jre17/jre17-android-x86_64.tar.xz"
            )
        )
        assertTrue(
            VerifiedAndroidRuntimeDownloader.isTrustedUrl(
                "https://release-assets.githubusercontent.com/asset"
            )
        )
        assertTrue(
            VerifiedAndroidRuntimeDownloader.isTrustedUrl(
                "https://objects.githubusercontent.com/asset"
            )
        )
        assertFalse(VerifiedAndroidRuntimeDownloader.isTrustedUrl("http://github.com/asset"))
        assertFalse(VerifiedAndroidRuntimeDownloader.isTrustedUrl("https://example.com/asset"))
        assertFalse(VerifiedAndroidRuntimeDownloader.isTrustedUrl("https://github.com.evil.test/asset"))
        assertFalse(VerifiedAndroidRuntimeDownloader.isTrustedUrl("https://user@github.com/asset"))
        assertFalse(VerifiedAndroidRuntimeDownloader.isTrustedUrl("https://github.com:444/asset"))
    }

    @Test
    fun rejectsModifiedPackageBeforeCreatingAnyDownload() {
        val original = AndroidRuntimePackageCatalog.exact(17, "x86_64")!!
        val modified = original.copy(downloadUrl = "https://example.com/malware.tar.xz")
        val root = Files.createTempDirectory("runtime-download-test").toFile()
        try {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                VerifiedAndroidRuntimeDownloader.download(modified, root)
            }
            assertEquals(0, root.listFiles()?.size ?: 0)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun catalogPackagesHaveExpectedArchiveAndDigestFields() {
        AndroidRuntimePackageCatalog.availableForAbi("arm64-v8a").forEach { runtime ->
            assertTrue(runtime.archiveFileName.matches(Regex("jre\\d+-android-arm64\\.tar\\.xz")))
            assertTrue(runtime.sha256.matches(Regex("[a-f0-9]{64}")))
            assertTrue(runtime.sizeBytes > 1_000_000L)
        }
    }
}
