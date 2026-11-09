package com.flame.launcher.runtime

/**
 * Fail-closed authorization gate for the future embedded-JVM invocation bridge.
 *
 * The current provider inspector emits BLOCKED/WARN/ERROR diagnostics only.
 * None of those states authorizes JNI_CreateJavaVM. A future implementation
 * must produce an explicit READY diagnostic after its runtime-isolation and
 * ART coexistence strategy has been verified on supported Android devices.
 */
sealed class JvmInvocationReadiness {
    data object Ready : JvmInvocationReadiness()
    data class Blocked(val diagnostic: String) : JvmInvocationReadiness()
    data class Unverified(val diagnostic: String) : JvmInvocationReadiness()
    data class Failed(val diagnostic: String) : JvmInvocationReadiness()

    val mayInvokeCreateJavaVm: Boolean
        get() = this is Ready

    companion object {
        fun fromDiagnostic(diagnostic: String): JvmInvocationReadiness {
            val normalized = diagnostic.trim()
            return when {
                normalized.startsWith("READY:") ->
                    Ready
                normalized.startsWith("BLOCKED:") ->
                    Blocked(normalized)
                normalized.startsWith("ERROR:") ->
                    Failed(normalized)
                normalized.startsWith("WARN:") ->
                    Unverified(normalized)
                else ->
                    Unverified(
                        if (normalized.isEmpty()) {
                            "No JNI invocation-provider diagnostic was supplied."
                        } else {
                            "Unrecognized JNI invocation-provider diagnostic: $normalized"
                        }
                    )
            }
        }
    }
}
