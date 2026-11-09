package com.flame.launcher.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmInvocationReadinessTest {
    @Test
    fun onlyExplicitReadyDiagnosticAuthorizesInvocation() {
        assertTrue(
            JvmInvocationReadiness.fromDiagnostic("READY: verified runtime isolation")
                .mayInvokeCreateJavaVm
        )
        assertFalse(
            JvmInvocationReadiness.fromDiagnostic("WARN: provider identity is inconclusive")
                .mayInvokeCreateJavaVm
        )
        assertFalse(
            JvmInvocationReadiness.fromDiagnostic("BLOCKED: process-global symbol is ART")
                .mayInvokeCreateJavaVm
        )
        assertFalse(
            JvmInvocationReadiness.fromDiagnostic("ERROR: inspection failed")
                .mayInvokeCreateJavaVm
        )
    }

    @Test
    fun unknownOrEmptyDiagnosticFailsClosed() {
        assertFalse(JvmInvocationReadiness.fromDiagnostic("").mayInvokeCreateJavaVm)
        assertFalse(JvmInvocationReadiness.fromDiagnostic("OK: library loaded").mayInvokeCreateJavaVm)
        assertFalse(JvmInvocationReadiness.fromDiagnostic("some unexpected output").mayInvokeCreateJavaVm)
    }

    @Test
    fun diagnosticClassificationPreservesReason() {
        val diagnostic = "BLOCKED: JNI_CreateJavaVM resolves to libart.so"
        val readiness = JvmInvocationReadiness.fromDiagnostic(diagnostic)

        assertTrue(readiness is JvmInvocationReadiness.Blocked)
        assertFalse(readiness.mayInvokeCreateJavaVm)
    }
}
