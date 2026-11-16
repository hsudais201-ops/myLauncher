package com.mflegend.flamelauncher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLaunchReadinessTest {
    @Test fun defaultsToLaunchDisabledWithEveryRequirementMissing() {
        val readiness = RuntimeLaunchReadiness()

        assertFalse(readiness.canLaunch)
        assertEquals(6, readiness.missingRequirements.size)
    }

    @Test fun abiOrRuntimeMetadataAloneCannotEnableLaunch() {
        // A matching ABI and a discovered libjvm.so are not proof that the
        // Android linker can load it or that Minecraft can render.
        val readiness = RuntimeLaunchReadiness(
            compatibleJvmLoaded = false
        )

        assertFalse(readiness.canLaunch)
        assertTrue(readiness.missingRequirements.any { it.contains("Java runtime") })
        assertTrue(readiness.missingRequirements.any { it.contains("physical Android device") })
    }

    @Test fun eachIndividualRequirementBlocksLaunch() {
        val complete = RuntimeLaunchReadiness(
            compatibleJvmLoaded = true,
            nativeDependenciesResolved = true,
            graphicsSurfaceReady = true,
            inputAndAudioReady = true,
            lifecycleHandlingReady = true,
            physicalDeviceGameTestPassed = true
        )
        val incompleteStates = listOf(
            complete.copy(compatibleJvmLoaded = false),
            complete.copy(nativeDependenciesResolved = false),
            complete.copy(graphicsSurfaceReady = false),
            complete.copy(inputAndAudioReady = false),
            complete.copy(lifecycleHandlingReady = false),
            complete.copy(physicalDeviceGameTestPassed = false)
        )

        incompleteStates.forEach { readiness ->
            assertFalse(readiness.canLaunch)
            assertEquals(1, readiness.missingRequirements.size)
        }
    }

    @Test fun everyVerifiedGateIsRequired() {
        val complete = RuntimeLaunchReadiness(
            compatibleJvmLoaded = true,
            nativeDependenciesResolved = true,
            graphicsSurfaceReady = true,
            inputAndAudioReady = true,
            lifecycleHandlingReady = true,
            physicalDeviceGameTestPassed = true
        )
        assertTrue(complete.canLaunch)
        assertTrue(complete.missingRequirements.isEmpty())

        val incomplete = complete.copy(physicalDeviceGameTestPassed = false)
        assertFalse(incomplete.canLaunch)
        assertEquals(1, incomplete.missingRequirements.size)
    }
}
