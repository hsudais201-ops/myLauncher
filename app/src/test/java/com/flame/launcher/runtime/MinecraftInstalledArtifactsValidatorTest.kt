package com.flame.launcher.runtime

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftInstalledArtifactsValidatorTest {
    private fun descriptor(bytes: ByteArray, path: String) = MinecraftDownloadDescriptor(
        url = "https://libraries.minecraft.net/$path",
        sha1 = MessageDigest.getInstance("SHA-1").digest(bytes)
            .joinToString("") { "%02x".format(it) },
        size = bytes.size.toLong(),
        path = path
    )

    private fun plan(bytes: ByteArray = byteArrayOf(1, 2, 3)) =
        MinecraftLaunchDependencyPlan(
            versionId = "1.21",
            mainClass = "net.minecraft.client.main.Main",
            requiredJavaMajorVersion = 21,
            assetIndexId = "17",
            downloads = listOf(
                MinecraftPlannedDownload(
                    MinecraftPlannedDownloadKind.CLIENT,
                    "versions/1.21/1.21.jar",
                    descriptor(bytes, "client.jar")
                ),
                MinecraftPlannedDownload(
                    MinecraftPlannedDownloadKind.LIBRARY,
                    "libraries/example.jar",
                    descriptor(byteArrayOf(4, 5), "example.jar")
                )
            ),
            classPath = listOf("versions/1.21/1.21.jar", "libraries/example.jar"),
            nativeLibraries = emptyList()
        )

    @Test
    fun reportsCompleteOnlyWhenEveryPlannedFileMatchesSizeAndSha1() {
        val root = Files.createTempDirectory("flame-installed-check-").toFile()
        try {
            val dependencyPlan = plan()
            File(root, "versions/1.21/1.21.jar").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            File(root, "libraries/example.jar").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(4, 5))
            }
            val assetBytes = byteArrayOf(6, 7)
            val asset = MinecraftPlannedDownload(
                MinecraftPlannedDownloadKind.ASSET,
                "assets/objects/aa/aabb",
                descriptor(assetBytes, "assets/objects/aa/aabb")
            )
            File(root, asset.relativePath).apply {
                parentFile.mkdirs()
                writeBytes(assetBytes)
            }

            val result = MinecraftInstalledArtifactsValidator().validate(
                root, dependencyPlan, listOf(asset)
            )
            assertEquals(
                MinecraftInstalledArtifactsValidation.Complete(3, 7L),
                result
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun identifiesMissingAndCorruptArtifactsSeparately() {
        val root = Files.createTempDirectory("flame-installed-incomplete-").toFile()
        try {
            File(root, "versions/1.21/1.21.jar").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(9, 9, 9))
            }
            val result = MinecraftInstalledArtifactsValidator().validate(root, plan())
            assertTrue(result is MinecraftInstalledArtifactsValidation.Incomplete)
            val incomplete = result as MinecraftInstalledArtifactsValidation.Incomplete
            assertEquals(listOf("libraries/example.jar"), incomplete.missingFiles)
            assertEquals(listOf("versions/1.21/1.21.jar"), incomplete.corruptFiles)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymbolicLinkArtifactDestinations() {
        val root = Files.createTempDirectory("flame-installed-symlink-").toFile()
        val outside = Files.createTempFile("flame-outside-", ".jar")
        try {
            val target = File(root, "versions/1.21/1.21.jar")
            target.parentFile.mkdirs()
            Files.createSymbolicLink(target.toPath(), outside)
            val result = MinecraftInstalledArtifactsValidator().validate(root, plan())
            assertTrue(result is MinecraftInstalledArtifactsValidation.Incomplete)
            assertEquals(
                listOf("versions/1.21/1.21.jar", "libraries/example.jar"),
                (result as MinecraftInstalledArtifactsValidation.Incomplete).missingFiles
            )
        } finally {
            root.deleteRecursively()
            Files.deleteIfExists(outside)
        }
    }

    @Test
    fun rejectsAssetEntriesInTheWrongPlanAndDuplicateDestinations() {
        val root = Files.createTempDirectory("flame-installed-plan-").toFile()
        try {
            val duplicate = plan().downloads.first().copy(
                kind = MinecraftPlannedDownloadKind.ASSET
            )
            val invalid = MinecraftInstalledArtifactsValidator().validate(root, plan(), listOf(duplicate))
            assertEquals(MinecraftInstalledArtifactsValidation.InvalidPlan, invalid)
        } finally {
            root.deleteRecursively()
        }
    }
}
