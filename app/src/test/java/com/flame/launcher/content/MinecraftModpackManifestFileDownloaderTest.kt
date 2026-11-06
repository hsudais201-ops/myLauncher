package com.flame.launcher.content

import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftModpackManifestFileDownloaderTest {
    private val payload = "verified modpack entry".toByteArray()
    private fun digest(algorithm: String) =
        MessageDigest.getInstance(algorithm).digest(payload).joinToString("") { "%02x".format(it) }

    private class FakeConnection(
        url: URL,
        private val bytes: ByteArray,
        private val status: Int
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentLengthLong() = bytes.size.toLong()
        override fun getInputStream() = ByteArrayInputStream(bytes)
    }

    private fun plan(): MinecraftModpackManifestInstallPlanner.Plan {
        val required = MinecraftModpackManifestParser.RemoteFile(
            "config/example.txt",
            listOf("https://cdn.modrinth.com/data/test/primary"),
            digest("SHA-1"),
            digest("SHA-512"),
            payload.size.toLong(),
            "required",
            "required"
        )
        val optional = required.copy(path = "config/optional.txt", clientEnvironment = "optional")
        return MinecraftModpackManifestInstallPlanner.Plan(
            requiredFiles = listOf(MinecraftModpackManifestInstallPlanner.PlannedFile(
                required.path, File("/instance/config/example.txt"), required
            )),
            optionalFiles = listOf(MinecraftModpackManifestInstallPlanner.PlannedFile(
                optional.path, File("/instance/config/optional.txt"), optional
            )),
            skippedClientUnsupported = 2
        )
    }

    @Test
    fun downloadsRequiredFilesAndLeavesOptionalFilesOutByDefault() {
        val root = Files.createTempDirectory("flame-mrpack-download").toFile()
        try {
            val downloader = MinecraftContentStagedDownloader { FakeConnection(it, payload, 200) }
            val result = MinecraftModpackManifestFileDownloader(downloader).download(plan(), root)
            assertTrue("Expected success, got $result", result is MinecraftModpackManifestDownloadResult.Downloaded)
            val downloaded = result as MinecraftModpackManifestDownloadResult.Downloaded
            assertEquals(1, downloaded.files.size)
            assertEquals(2, downloaded.skippedClientUnsupported)
            assertEquals(1, downloaded.optionalFilesOmitted)
            assertTrue(downloaded.files.single().stagedFile.readBytes().contentEquals(payload))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun optionalFilesAreIncludedOnlyWhenRequested() {
        val root = Files.createTempDirectory("flame-mrpack-download").toFile()
        try {
            val downloader = MinecraftContentStagedDownloader { FakeConnection(it, payload, 200) }
            val result = MinecraftModpackManifestFileDownloader(downloader)
                .download(plan(), root, includeOptional = true)
            assertTrue(result is MinecraftModpackManifestDownloadResult.Downloaded)
            val downloaded = result as MinecraftModpackManifestDownloadResult.Downloaded
            assertEquals(2, downloaded.files.size)
            assertEquals(0, downloaded.optionalFilesOmitted)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun failedChecksumStopsImportAndRemovesEarlierStagedFiles() {
        val root = Files.createTempDirectory("flame-mrpack-download").toFile()
        try {
            val p = plan()
            val badFile = p.optionalFiles.single().file.copy(sha1 = "0".repeat(40), sha512 = null)
            val badPlan = p.copy(optionalFiles = listOf(p.optionalFiles.single().copy(file = badFile)))
            val downloader = MinecraftContentStagedDownloader { FakeConnection(it, payload, 200) }
            val result = MinecraftModpackManifestFileDownloader(downloader)
                .download(badPlan, root, includeOptional = true)
            assertTrue(result is MinecraftModpackManifestDownloadResult.Failed)
            assertEquals(0, root.walkTopDown().count { it.isFile })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun triesNextTrustedMirrorWhenFirstUrlFails() {
        val root = Files.createTempDirectory("flame-mrpack-download").toFile()
        try {
            val p = plan()
            val file = p.requiredFiles.single().file.copy(downloads = listOf(
                "https://cdn.modrinth.com/data/test/primary",
                "https://cdn.modrinth.com/data/test/mirror"
            ))
            val updated = p.copy(requiredFiles = listOf(p.requiredFiles.single().copy(file = file)))
            val downloader = MinecraftContentStagedDownloader { url ->
                FakeConnection(url, payload, if (url.path.endsWith("primary")) 503 else 200)
            }
            val result = MinecraftModpackManifestFileDownloader(downloader).download(updated, root)
            assertTrue("Expected mirror fallback success, got $result", result is MinecraftModpackManifestDownloadResult.Downloaded)
        } finally {
            root.deleteRecursively()
        }
    }
}
