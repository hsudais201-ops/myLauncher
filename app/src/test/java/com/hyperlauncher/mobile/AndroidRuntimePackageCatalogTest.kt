package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRuntimePackageCatalogTest {
    @Test
    fun exposesOnlyPinnedAndroidPackagesForSupportedAbis() {
        for (abi in listOf("arm64-v8a", "x86_64")) {
            val packages = AndroidRuntimePackageCatalog.availableForAbi(abi)
            assertEquals(listOf(8, 17, 21, 25), packages.map { it.javaMajor })
            packages.forEach { runtime ->
                assertEquals(64, runtime.sha256.length)
                assertTrue(runtime.sha256.all { it in "0123456789abcdef" })
                assertTrue(runtime.sizeBytes > 0L)
                assertTrue(runtime.downloadUrl.startsWith("https://github.com/AngelAuraMC/"))
                assertTrue(runtime.archiveFileName.endsWith(".tar.xz"))
            }
        }
    }

    @Test
    fun choosesLowestJavaMajorThatMeetsRequirement() {
        assertEquals(17, AndroidRuntimePackageCatalog.compatible(17, "x86_64")?.javaMajor)
        assertEquals(21, AndroidRuntimePackageCatalog.compatible(18, "x86_64")?.javaMajor)
        assertEquals(25, AndroidRuntimePackageCatalog.compatible(22, "arm64-v8a")?.javaMajor)
        assertNull(AndroidRuntimePackageCatalog.compatible(26, "x86_64"))
    }

    @Test
    fun doesNotConfuseDesktopOrUnknownAbisWithAndroidPackages() {
        assertNull(AndroidRuntimePackageCatalog.exact(21, "linux-x86_64"))
        assertNull(AndroidRuntimePackageCatalog.exact(21, "armeabi-v7a"))
        assertNotNull(AndroidRuntimePackageCatalog.exact(21, "arm64-v8a"))
    }

    @Test
    fun usesVersionedReleaseAssetsAndExpectedNames() {
        val runtime = AndroidRuntimePackageCatalog.exact(17, "x86_64")!!
        assertEquals("jre17-android-x86_64.tar.xz", runtime.archiveFileName)
        assertTrue(runtime.downloadUrl.endsWith("/download_jre17/jre17-android-x86_64.tar.xz"))
    }
}
