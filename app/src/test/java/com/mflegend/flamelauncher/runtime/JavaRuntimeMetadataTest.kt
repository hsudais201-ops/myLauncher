package com.mflegend.flamelauncher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaRuntimeMetadataTest {
    @Test fun parsesLegacyAndModernJavaVersions() {
        assertEquals(8, JavaRuntimeMetadata.parseJavaMajor("1.8.0_402"))
        assertEquals(17, JavaRuntimeMetadata.parseJavaMajor("17.0.9"))
        assertEquals(21, JavaRuntimeMetadata.parseJavaMajor("21-ea"))
    }

    @Test fun rejectsMalformedJavaVersions() {
        assertNull(JavaRuntimeMetadata.parseJavaMajor(""))
        assertNull(JavaRuntimeMetadata.parseJavaMajor("not-a-version"))
        assertNull(JavaRuntimeMetadata.parseJavaMajor("preview-17"))
    }

    @Test fun parsesQuotedReleaseMetadataAndIgnoresInvalidKeys() {
        val parsed = JavaRuntimeMetadata.parseReleaseFile(
            "JAVA_VERSION=\"17.0.9\"\nOS_ARCH=\"aarch64\"\ninvalid-key=\"ignored\"\n"
        )
        assertEquals("17.0.9", parsed["JAVA_VERSION"])
        assertEquals("aarch64", parsed["OS_ARCH"])
        assertFalse(parsed.containsKey("invalid-key"))
    }

    @Test fun normalizesCommonArchitectureAliases() {
        assertEquals("arm64", JavaRuntimeMetadata.normalizeArchitecture("AARCH64"))
        assertEquals("arm64", JavaRuntimeMetadata.normalizeArchitecture("arm64-v8a"))
        assertEquals("x86_64", JavaRuntimeMetadata.normalizeArchitecture("AMD64"))
        assertEquals("x86", JavaRuntimeMetadata.normalizeArchitecture("i686"))
    }

    @Test fun arm64MatchRequiresBoth64BitProcessAndAdvertisedAbi() {
        assertTrue(JavaRuntimeMetadata.matchesInitialArm64Process("arm64", true, listOf("arm64-v8a")))
        assertFalse(JavaRuntimeMetadata.matchesInitialArm64Process("arm64", false, listOf("arm64-v8a")))
        assertFalse(JavaRuntimeMetadata.matchesInitialArm64Process("arm64", true, listOf("armeabi-v7a")))
        assertFalse(JavaRuntimeMetadata.matchesInitialArm64Process("x86_64", true, listOf("arm64-v8a")))
    }
}
