package com.flame.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.Signature
import java.nio.file.Files

class MojoRuntimeComponentDownloaderTest {
    @Test
    fun downloadsAndPromotesOnlyAuthenticComponentBytes() {
        val payload = "verified-runtime-component".toByteArray()
        val pair = keyPair()
        val signature = sign(payload, pair.private)
        val directory = Files.createTempDirectory("mojo-runtime-test").toFile()
        try {
            val downloader = MojoRuntimeComponentDownloader(
                connectionFactory = { url -> FakeConnection(url, payload) }
            )
            val result = downloader.download(
                address = COMPONENT_URL,
                expectedSignature = signature,
                publicKey = pair.public,
                destination = File(directory, "universal.tar.xz")
            )
            assertEquals(payload.size.toLong(), result.sizeBytes)
            assertEquals(payload.toList(), result.file.readBytes().toList())
            assertTrue(result.sha256.matches(Regex("[a-f0-9]{64}")))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsTamperedComponentAndDoesNotPromoteIt() {
        val signedPayload = "authentic".toByteArray()
        val pair = keyPair()
        val signature = sign(signedPayload, pair.private)
        val directory = Files.createTempDirectory("mojo-runtime-test").toFile()
        val destination = File(directory, "universal.tar.xz")
        try {
            val downloader = MojoRuntimeComponentDownloader(
                connectionFactory = { url -> FakeConnection(url, "tampered".toByteArray()) }
            )
            assertThrows(IllegalArgumentException::class.java) {
                downloader.download(COMPONENT_URL, signature, pair.public, destination)
            }
            assertFalse(destination.exists())
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsUntrustedHostBeforeOpeningConnection() {
        val pair = keyPair()
        val downloader = MojoRuntimeComponentDownloader(
            connectionFactory = { throw AssertionError("Connection must not be opened.") }
        )
        assertThrows(IllegalArgumentException::class.java) {
            downloader.download(
                "https://example.com/jre-download/components/jre-21/universal.tar.xz",
                ByteArray(512),
                pair.public,
                File("unused.tar.xz")
            )
        }
    }

    private fun keyPair() = KeyPairGenerator.getInstance("RSA").apply {
        initialize(4096)
    }.generateKeyPair()

    private fun sign(payload: ByteArray, privateKey: java.security.PrivateKey): ByteArray =
        Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(payload)
            sign()
        }

    private class FakeConnection(url: URL, private val payload: ByteArray) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode(): Int = HTTP_OK
        override fun getContentLengthLong(): Long = payload.size.toLong()
        override fun getInputStream() = ByteArrayInputStream(payload)
    }

    companion object {
        private const val COMPONENT_URL =
            "https://mojolauncher.github.io/jre-download/components/jre-21/universal.tar.xz"
    }
}
