package com.voidcraft.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MinecraftArtifactInstallServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun coordinatesPreparationExecutionStagesAndProgress() {
        val fixture = fixture()
        val stages = mutableListOf<MinecraftArtifactInstallStage>()
        val progress = mutableListOf<InstallArtifactProgress>()
        val executor = RecordingExecutor()
        val service = MinecraftArtifactInstallService(
            preparer = MinecraftInstallPreparer { fixture.preparation },
            executor = executor
        )

        val result = service.installArtifacts(
            entry = fixture.entry,
            installRoot = File(temporaryFolder.root, "game"),
            onStage = { stages.add(it) },
            onProgress = { progress.add(it) }
        )

        assertEquals(
            listOf(
                MinecraftArtifactInstallStage.PREPARING_METADATA,
                MinecraftArtifactInstallStage.DOWNLOADING_ARTIFACTS,
                MinecraftArtifactInstallStage.COMPLETE
            ),
            stages
        )
        assertEquals(fixture.plan, executor.receivedPlan)
        assertEquals(File(temporaryFolder.root, "game").canonicalFile, executor.receivedRoot)
        assertEquals(1, progress.size)
        assertEquals(1L, progress.single().fileProgress.bytesDownloaded)
        assertEquals(fixture.entry.id, result.execution.versionId)
        assertEquals(File(temporaryFolder.root, "game").canonicalFile, result.installRoot)
    }

    @Test
    fun incompletePlanFailsBeforeExecutorRuns() {
        val fixture = fixture()
        val badPreparation = fixture.preparation.copy(
            plan = fixture.plan.copy(warnings = listOf("native architecture is unresolved"))
        )
        val executor = RecordingExecutor()
        val service = MinecraftArtifactInstallService(
            preparer = MinecraftInstallPreparer { badPreparation },
            executor = executor
        )

        val error = assertThrows(IncompleteInstallPlanException::class.java) {
            service.installArtifacts(fixture.entry, File(temporaryFolder.root, "game"))
        }

        assertTrue(error.reasons.contains("native architecture is unresolved"))
        assertEquals(null, executor.receivedPlan)
        assertFalse(File(temporaryFolder.root, "game").exists())
    }

    @Test
    fun cancellationAfterMetadataPreparationStopsBeforeExecutor() {
        val fixture = fixture()
        val executor = RecordingExecutor()
        val service = MinecraftArtifactInstallService(
            preparer = MinecraftInstallPreparer { fixture.preparation },
            executor = executor
        )
        var checks = 0

        assertThrows(DownloadCancelledException::class.java) {
            service.installArtifacts(
                fixture.entry,
                File(temporaryFolder.root, "game"),
                isCancelled = { ++checks >= 2 }
            )
        }

        assertEquals(null, executor.receivedPlan)
        assertFalse(File(temporaryFolder.root, "game").exists())
    }

    @Test
    fun mismatchedPreparedVersionIsRejected() {
        val fixture = fixture()
        val badPreparation = fixture.preparation.copy(
            plan = fixture.plan.copy(versionId = "1.20.1")
        )
        val executor = RecordingExecutor()
        val service = MinecraftArtifactInstallService(
            preparer = MinecraftInstallPreparer { badPreparation },
            executor = executor
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.installArtifacts(fixture.entry, File(temporaryFolder.root, "game"))
        }

        assertEquals(null, executor.receivedPlan)
    }

    private fun fixture(): Fixture {
        val sha1 = "0123456789abcdef0123456789abcdef01234567"
        val entry = MinecraftVersionEntry(
            id = "1.21.4",
            type = "release",
            metadataUrl = "https://piston-meta.mojang.com/versions/1.21.4.json",
            updatedAt = "2025-01-01T00:00:00Z",
            releaseTime = "2025-01-01T00:00:00Z",
            sha1 = sha1,
            complianceLevel = 1
        )
        val details = MinecraftVersionDetails(
            id = entry.id,
            type = entry.type,
            mainClass = "net.minecraft.client.main.Main",
            inheritsFrom = null,
            assets = null,
            assetIndex = null,
            clientDownload = null,
            javaMajorVersion = 21
        )
        val artifact = MinecraftPlannedDownload(
            kind = MinecraftInstallPlanner.KIND_VERSION_METADATA,
            destinationRelativePath = "versions/1.21.4/1.21.4.json",
            url = entry.metadataUrl,
            expectedSha1 = sha1,
            expectedBytes = 1L,
            logicalName = entry.id
        )
        val plan = MinecraftInstallPlan(
            versionId = entry.id,
            javaMajorVersion = 21,
            mainClass = details.mainClass,
            downloads = listOf(artifact),
            warnings = emptyList()
        )
        val preparation = MinecraftInstallPreparation(entry, details, null, plan)
        return Fixture(entry, plan, preparation, sha1)
    }

    private data class Fixture(
        val entry: MinecraftVersionEntry,
        val plan: MinecraftInstallPlan,
        val preparation: MinecraftInstallPreparation,
        val sha1: String
    )

    private class RecordingExecutor : MinecraftPlanExecutor {
        var receivedPlan: MinecraftInstallPlan? = null
        var receivedRoot: File? = null

        override fun execute(
            plan: MinecraftInstallPlan,
            installRoot: File,
            isCancelled: () -> Boolean,
            onProgress: (InstallArtifactProgress) -> Unit
        ): MinecraftInstallResult {
            receivedPlan = plan
            receivedRoot = installRoot
            assertFalse(isCancelled())
            onProgress(InstallArtifactProgress(
                artifactIndex = 1,
                artifactCount = plan.downloads.size,
                artifact = plan.downloads.first(),
                fileProgress = FileDownloadProgress(1L, 1L)
            ))
            return MinecraftInstallResult(
                versionId = plan.versionId,
                receipts = listOf(
                    FileDownloadReceipt(
                        file = File(installRoot, "versions/1.21.4/1.21.4.json"),
                        bytesDownloaded = 1L,
                        sha1 = "0123456789abcdef0123456789abcdef01234567",
                        sha256 = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                        attempts = 1
                    )
                )
            )
        }
    }
}
