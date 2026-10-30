package com.hyperlauncher.mobile

import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException

class MinecraftInstallationServiceTest {
    @Test
    fun cancellationStopsBeforeStartingTheFirstDownload() {
        val descriptor = MinecraftDownload(
            url = "https://piston-data.mojang.com/client.jar",
            sha1 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            size = 1L
        )
        val planned = PlannedMinecraftFile(
            descriptor = descriptor,
            relativePath = "versions/test/test.jar",
            kind = "client"
        )
        val plan = MinecraftInstallPlan(
            versionId = "test",
            clientJar = planned,
            assetIndexFile = planned.copy(relativePath = "assets/indexes/test.json", kind = "asset-index"),
            assetIndex = MinecraftAssetIndex("test", emptyList(), 0L),
            assets = emptyList(),
            totalAssetBytes = 0L
        )

        assertThrows(CancellationException::class.java) {
            MinecraftInstallationService.install(
                plan = plan,
                storageRoot = File(System.getProperty("java.io.tmpdir"), "hyper-launcher-cancel-test"),
                shouldCancel = { true }
            )
        }
    }
}
