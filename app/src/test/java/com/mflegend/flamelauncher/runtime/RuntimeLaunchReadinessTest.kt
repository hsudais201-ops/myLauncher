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
