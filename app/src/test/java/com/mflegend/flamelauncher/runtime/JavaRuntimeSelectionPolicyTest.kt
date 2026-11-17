package com.mflegend.flamelauncher.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JavaRuntimeSelectionPolicyTest {
    @Test
    fun selectsOnlyExactMajorVersionAndProcessAbi() {
        val java17 = runtime("java-17", 17, matchesAbi = true)
        val java21WrongAbi = runtime("java-21-other-abi", 21, matchesAbi = false)
        val java21 = runtime("java-21", 21, matchesAbi = true)

        val selected = JavaRuntimeSelectionPolicy.findExactMajor(
            21,
            listOf(java17, java21WrongAbi, java21)
        )

        assertEquals("java-21", selected?.id)
    }

    @Test
    fun refusesUnknownOrInvalidVersionRequirements() {
        val candidates = listOf(runtime("java-21", 21, matchesAbi = true))
        assertNull(JavaRuntimeSelectionPolicy.findExactMajor(null, candidates))
        assertNull(JavaRuntimeSelectionPolicy.findExactMajor(0, candidates))
        assertNull(JavaRuntimeSelectionPolicy.findExactMajor(17, candidates))
    }

    private fun runtime(id: String, major: Int, matchesAbi: Boolean) =
        AndroidJavaRuntimeManager.InstalledRuntime(
            id = id,
            javaVersion = major.toString() + ".0.0",
            javaMajorVersion = major,
            architecture = "arm64",
            homeDirectory = File("/runtime/$id"),
            jvmLibrary = File("/runtime/$id/lib/server/libjvm.so"),
            matchesProcessAbi = matchesAbi
        )
}
