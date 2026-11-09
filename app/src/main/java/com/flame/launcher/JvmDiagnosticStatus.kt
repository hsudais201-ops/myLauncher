package com.flame.launcher

/**
 * Returns true only for a clean success diagnostic.
 *
 * Native runtime preparation may successfully load libjvm while separately
 * reporting that JNI invocation is blocked or unverified. UI status must not
 * collapse those mixed diagnostics into a green success state.
 */
internal fun isSuccessfulJvmDiagnostic(diagnostic: String): Boolean {
    val normalized = diagnostic.trim()
    if (!normalized.startsWith("OK:")) return false
    return listOf("BLOCKED:", "WARN:", "ERROR:").none(normalized::contains)
}
