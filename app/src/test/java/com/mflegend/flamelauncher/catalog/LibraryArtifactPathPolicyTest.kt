package com.mflegend.flamelauncher.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryArtifactPathPolicyTest {
    @Test fun acceptsSafeMavenArtifactPaths() {
        assertEquals(
            listOf("com", "example", "library", "1.2.3", "library-1.2.3.jar"),
            LibraryArtifactPathPolicy.segments("com/example/library/1.2.3/library-1.2.3.jar")
        )
        assertEquals(
            listOf("org", "example", "name+variant.jar"),
            LibraryArtifactPathPolicy.segments("org/example/name+variant.jar")
        )
    }

    @Test fun rejectsTraversalAndAbsolutePaths() {
        listOf(
            "../outside.jar",
            "org/../../outside.jar",
            "/absolute/library.jar",
            "org/./library.jar",
            "org//library.jar"
        ).forEach { path ->
            assertRejected(path)
        }
    }

    @Test fun rejectsSeparatorsAndUnexpectedCharacters() {
        listOf(
            "org\\example\\library.jar",
            "org/example/name with spaces.jar",
            "org/example/name?query.jar",
            "org/example/name#fragment.jar",
            "org/example/\\u0000name.jar"
        ).forEach { path ->
            assertRejected(path)
        }
    }

    @Test fun rejectsExcessivelyLongOrDeepPaths() {
        assertRejected("a".repeat(1025))
        assertRejected((1..33).joinToString("/") { "segment" })
    }

    private fun assertRejected(path: String) {
        assertTrue(
            "Expected unsafe path to be rejected: $path",
            runCatching { LibraryArtifactPathPolicy.segments(path) }
                .exceptionOrNull() is IllegalArgumentException
        )
    }
}
