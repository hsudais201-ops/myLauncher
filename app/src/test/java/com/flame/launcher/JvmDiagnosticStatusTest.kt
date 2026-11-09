package com.flame.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmDiagnosticStatusTest {
    @Test
    fun cleanOkDiagnosticIsSuccessful() {
        assertTrue(isSuccessfulJvmDiagnostic("OK: JVM dependencies loaded."))
        assertTrue(isSuccessfulJvmDiagnostic("  OK: runtime prepared  "))
    }

    @Test
    fun mixedSuccessAndUnsafeInvocationMarkersAreNotSuccessful() {
        assertFalse(isSuccessfulJvmDiagnostic("OK: libraries loaded. WARN: JNI provider is inconclusive."))
        assertFalse(isSuccessfulJvmDiagnostic("OK: libraries loaded. BLOCKED: JNI provider is ART."))
        assertFalse(isSuccessfulJvmDiagnostic("OK: libraries loaded. ERROR: invocation inspection failed."))
    }

    @Test
    fun nonSuccessAndUnknownDiagnosticsAreNotSuccessful() {
        assertFalse(isSuccessfulJvmDiagnostic("WARN: not verified"))
        assertFalse(isSuccessfulJvmDiagnostic("BLOCKED: ART provider"))
        assertFalse(isSuccessfulJvmDiagnostic("ERROR: native loader failed"))
        assertFalse(isSuccessfulJvmDiagnostic("READY: not a preparation success"))
        assertFalse(isSuccessfulJvmDiagnostic(""))
    }
}
