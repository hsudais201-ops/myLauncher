package com.voidcraft.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class MinecraftInstallVerifierTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val bytes = "verified test artifact".toByteArray()

    @Test
    fun reportsMissingThenVerifiedFilesReadOnly() {
        val root = temporaryFolder.newFolder("game-root")
        val plan = plan()
        val verifier = MinecraftInstallVerifier()

        val before = verifier.inspect(plan, root)
        assertEquals(1, before.needsDownload.size)
        assertEquals(ArtifactVerificationState.MISSING, before.artifacts.single().state)
        assertTrue(root.listFiles().orEmpty().isEmpty())

        val target = File(root, plan.downloads.single().destinationRelativePath)
        assertTrue(target.parentFile.mkdirs())
        target.writeBytes(bytes)

        val after = verifier.inspect(plan, root)
        assertEquals(1, after.verifiedCount)
        assertTrue(after.isFullyVerified)
        assertEquals(ArtifactVerificationState.VERIFIED, after.artifacts.single().state)
        assertTrue(after.artifacts.single().cachedReceipt?.wasCached == true)
    }

    @Test
    fun reportsSizeChecksumAndUnavailableHashCases() {
        val root = temporaryFolder.newFolder("game-root")
        val target = File(root, "versions/1.21.4/test.jar")
        assertTrue(target.parentFile.mkdirs())

        target.writeText("wrong size")
        val sizeReport = MinecraftInstallVerifier().inspect(plan(), root)
        assertEquals(ArtifactVerificationState.SIZE_MISMATCH, sizeReport.artifacts.single().state)

        target.writeBytes("same length bad bytes!".toByteArray().copyOf(bytes.size))
        val hashReport = MinecraftInstallVerifier().inspect(plan(), root)
        assertEquals(ArtifactVerificationState.CHECKSUM_MISMATCH, hashReport.artifacts.single().state)

        val noHash = plan(expectedSha1 = null)
        target.writeBytes(bytes)
        val noHashReport = MinecraftInstallVerifier().inspect(noHash, root)
        assertEquals(ArtifactVerificationState.UNVERIFIABLE, noHashReport.artifacts.single().state)
        assertFalse(noHashReport.isFullyVerified)
    }

    @Test
    fun invalidDestinationAndUrlProduceInvalidPlanReport() {
        val root = temporaryFolder.newFolder("game-root")
        val pathPlan = plan().copy(downloads = listOf(artifact(path = "saves/world.dat")))
        val pathReport = MinecraftInstallVerifier().inspect(pathPlan, root)
        assertEquals(ArtifactVerificationState.INVALID_PLAN, pathReport.artifacts.single().state)

        val urlPlan = plan().copy(downloads = listOf(artifact(url = "https://evil.invalid/file.jar")))
        val urlReport = MinecraftInstallVerifier().inspect(urlPlan, root)
        assertEquals(ArtifactVerificationState.INVALID_PLAN, urlReport.artifacts.single().state)
    }

    private fun plan(expectedSha1: String? = digest(bytes)) = MinecraftInstallPlan(
        versionId = "1.21.4",
        javaMajorVersion = 21,
        mainClass = "net.minecraft.client.main.Main",
        downloads = listOf(artifact(expectedSha1 = expectedSha1)),
        warnings = emptyList()
    )

    private fun artifact(
        path: String = "versions/1.21.4/test.jar",
        url: String = "https://piston-data.mojang.com/test.jar",
        expectedSha1: String? = digest(bytes)
    ) = MinecraftPlannedDownload(
        kind = MinecraftInstallPlanner.KIND_CLIENT,
        destinationRelativePath = path,
        url = url,
        expectedSha1 = expectedSha1,
        expectedBytes = bytes.size.toLong()
    )

    private fun digest(value: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(value).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
}
