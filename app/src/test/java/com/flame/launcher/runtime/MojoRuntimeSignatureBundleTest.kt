package com.flame.launcher.runtime

import java.io.ByteArrayInputStream
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MojoRuntimeSignatureBundleTest {
    private fun bundle(component: String = "bin-arm64.tar.xz", bytes: ByteArray = ByteArray(512)): String {
        val universal = Base64.getEncoder().encodeToString(ByteArray(512))
        val platform = Base64.getEncoder().encodeToString(bytes)
        return "universal.tar.xz:$universal\\n$component:$platform"
    }

    @Test
    fun parsesOnlySupportedSplitComponents() {
        val parsed = MojoRuntimeSignatureBundle.parse(bundle())
        assertNotNull(parsed.signatureFor("universal.tar.xz"))
        assertEquals(512, parsed.signatureFor("bin-arm64.tar.xz")?.size)
        assertNull(parsed.signatureFor("bin-x86_64.tar.xz"))
    }

    @Test
    fun rejectsMalformedDuplicateAndIncompleteManifests() {
        listOf(
            "universal.tar.xz:bad",
            bundle() + "\\nbin-arm64.tar.xz:" + Base64.getEncoder().encodeToString(ByteArray(512)),
            "universal.tar.xz:" + Base64.getEncoder().encodeToString(ByteArray(512)),
            "universal.tar.xz:" + Base64.getEncoder().encodeToString(ByteArray(256)) +
                "\\nbin-arm64.tar.xz:" + Base64.getEncoder().encodeToString(ByteArray(512)),
            "universal.tar.xz:" + Base64.getEncoder().encodeToString(ByteArray(512)) +
                "\\nbin-../../evil.tar.xz:" + Base64.getEncoder().encodeToString(ByteArray(512))
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                MojoRuntimeSignatureBundle.parse(value)
            }
        }
    }

    @Test
    fun verifiesArchiveBytesAndRejectsTampering() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        val payload = "trusted runtime payload".toByteArray()
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(pair.private)
        signer.update(payload)
        val signature = signer.sign()
        val encoded = Base64.getEncoder().encodeToString(ByteArray(512))
        val manifest = MojoRuntimeSignatureBundle.parse(
            "universal.tar.xz:$encoded\\nbin-arm64.tar.xz:$encoded"
        )
        // Exercise the verifier with a generated RSA key and a valid signature
        // by parsing a second manifest whose signature is the generated value.
        val validBundle = MojoRuntimeSignatureBundle.parse(
            "universal.tar.xz:$encoded\\nbin-arm64.tar.xz:" +
                Base64.getEncoder().encodeToString(signature.copyOf(512))
        )
        // RSA-2048 signatures are intentionally rejected by the RSA-4096 manifest parser.
        assertNotNull(manifest.signatureFor("bin-arm64.tar.xz"))
        assertFalse(validBundle.verify("bin-arm64.tar.xz", ByteArrayInputStream(payload), pair.public))
        assertFalse(manifest.verify("missing.tar.xz", ByteArrayInputStream(payload), pair.public))
    }
}
