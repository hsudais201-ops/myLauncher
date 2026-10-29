package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MinecraftVersionManifestTest {
    @Test
    fun parsesReleaseAndSnapshotAndSkipsUnsafeEntries() {
        val json = """
            {
              "versions": [
                {"id":"1.21.4","type":"release","releaseTime":"2024-12-03T00:00:00+00:00"},
                {"id":"24w14a","type":"snapshot","releaseTime":"2024-04-02T00:00:00+00:00"},
                {"id":"../bad","type":"release","releaseTime":"ignored"},
                {"id":"1.19.4","type":"old_beta","releaseTime":"ignored"}
              ]
            }
        """.trimIndent()

        val versions = MinecraftVersionManifest.parseManifest(json)

        assertEquals(2, versions.size)
        assertEquals(MinecraftVersion("1.21.4", "release", "2024-12-03T00:00:00+00:00"), versions[0])
        assertEquals(MinecraftVersion("24w14a", "snapshot", "2024-04-02T00:00:00+00:00"), versions[1])
    }

    @Test
    fun rejectsManifestWithNoUsableVersions() {
        assertThrows(IllegalStateException::class.java) {
            MinecraftVersionManifest.parseManifest("""{"versions":[{"id":"../bad","type":"release"}]}""")
        }
    }
}
