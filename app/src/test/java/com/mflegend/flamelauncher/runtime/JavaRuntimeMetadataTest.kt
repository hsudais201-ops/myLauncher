package com.mflegend.flamelauncher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class JavaRuntimeMetadataTest {
    @Test fun parsesLegacyAndModernJavaVersions() {
        assertEquals(8, JavaRuntimeMetadata.parseJavaMajor("1.8.0_402"))
        assertEquals(17, JavaRuntimeMetadata.parseJavaMajor("17.0.9"))
        assertEquals(21, JavaRuntimeMetadata.parseJavaMajor("21-ea"))
        assertEquals(17, JavaRuntimeMetadata.parseJavaMajor("17.0.9+9-LTS"))
    }

    @Test fun rejectsMalformedJavaVersions() {
        assertNull(JavaRuntimeMetadata.parseJavaMajor(""))
        assertNull(JavaRuntimeMetadata.parseJavaMajor("not-a-version"))
        assertNull(JavaRuntimeMetadata.parseJavaMajor("preview-17"))
        assertNull(JavaRuntimeMetadata.parseJavaMajor("17garbage"))
        assertNull(JavaRuntimeMetadata.parseJavaMajor("17-"))
    }

    @Test fun parsesQuotedReleaseMetadataAndIgnoresInvalidKeys() {
        val parsed = JavaRuntimeMetadata.parseReleaseFile(
            "JAVA_VERSION=\"17.0.9\"\nOS_ARCH=\"aarch64\"\ninvalid-key=\"ignored\"\n"
        )
        assertEquals("17.0.9", parsed["JAVA_VERSION"])
        assertEquals("aarch64", parsed["OS_ARCH"])
        assertFalse(parsed.containsKey("invalid-key"))
    }

    @Test fun rejectsMismatchedQuotesAndDuplicateReleaseKeys() {
        val parsed = JavaRuntimeMetadata.parseReleaseFile(
            "JAVA_VERSION=\"17.0.9\nOS_ARCH=\"aarch64\"\nIMPLEMENTOR=\"OpenJDK\"\nIMPLEMENTOR=\"Other\"\n"
        )
        assertFalse(parsed.containsKey("JAVA_VERSION"))
        assertFalse(parsed.containsKey("IMPLEMENTOR"))
        assertEquals("aarch64", parsed["OS_ARCH"])
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

    @Test fun runtimeIdsRejectTraversalAndUnexpectedCharacters() {
        assertTrue(RuntimePathPolicy.isRuntimeId("java17-arm64"))
        assertTrue(RuntimePathPolicy.isRuntimeId("runtime.v2_1"))
        assertFalse(RuntimePathPolicy.isRuntimeId(""))
        assertFalse(RuntimePathPolicy.isRuntimeId("../outside"))
        assertFalse(RuntimePathPolicy.isRuntimeId("runtime/name"))
        assertFalse(RuntimePathPolicy.isRuntimeId("runtime name"))
        assertFalse(RuntimePathPolicy.isRuntimeId("x".repeat(81)))
    }

    @Test fun directChildRequiresExactlyOneDirectoryLevel() {
        withTempDirectory { root ->
            val child = Files.createDirectory(root.resolve("java17")).toFile()
            val nested = Files.createDirectory(child.toPath().resolve("nested")).toFile()
            val sibling = Files.createDirectory(root.resolveSibling(root.fileName.toString() + "-other")).toFile()
            try {
                assertTrue(RuntimePathPolicy.isDirectChild(root.toFile(), child))
                assertFalse(RuntimePathPolicy.isDirectChild(root.toFile(), nested))
                assertFalse(RuntimePathPolicy.isDirectChild(root.toFile(), sibling))
            } finally {
                sibling.deleteRecursively()
            }
        }
    }

    @Test fun safeChildAcceptsNestedFilesButRejectsSiblingPrefixTraversal() {
        withTempDirectory { root ->
            val nested = Files.createDirectories(root.resolve("runtime/lib/server"))
            val file = Files.createFile(nested.resolve("libjvm.so")).toFile()
            val sibling = root.resolveSibling(root.fileName.toString() + "-escape").resolve("secret")
            assertTrue(RuntimePathPolicy.isSafeChild(root.toFile(), file))
            assertFalse(RuntimePathPolicy.isSafeChild(root.toFile(), sibling.toFile()))
        }
    }

    @Test fun rejectsNestedSymlinkThatResolvesInsideManagedDirectory() {
        withTempDirectory { root ->
            val realDirectory = Files.createDirectories(root.resolve("real/lib/server"))
            Files.createFile(realDirectory.resolve("libjvm.so"))
            val linkedDirectory = root.resolve("linked")
            Files.createSymbolicLink(linkedDirectory, root.resolve("real"))
            assertFalse(RuntimePathPolicy.isSafeChild(root.toFile(), linkedDirectory.resolve("lib/server/libjvm.so").toFile()))
        }
    }

    @Test fun rejectsSymlinkThatResolvesOutsideManagedDirectory() {
        withTempDirectory { root ->
            withTempDirectory { outside ->
                val link = root.resolve("runtime-link")
                Files.createSymbolicLink(link, outside)
                assertFalse(RuntimePathPolicy.isDirectChild(root.toFile(), link.toFile()))
                assertFalse(RuntimePathPolicy.isSafeChild(root.toFile(), link.resolve("release").toFile()))
            }
        }
    }

    @Test fun rejectsSymlinksEvenWhenTheyPointInsideTheManagedDirectory() {
        withTempDirectory { root ->
            val target = Files.createDirectory(root.resolve("real-runtime"))
            val link = root.resolve("linked-runtime")
            Files.createSymbolicLink(link, target)
            assertTrue(RuntimePathPolicy.isSymbolicLink(link.toFile()))
            assertTrue(RuntimePathPolicy.isDirectChild(root.toFile(), link.toFile()))
        }
    }

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("flame-runtime-path-test")
        try {
            block(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
