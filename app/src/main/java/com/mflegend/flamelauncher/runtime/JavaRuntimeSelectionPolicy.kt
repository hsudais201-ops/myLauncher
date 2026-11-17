package com.mflegend.flamelauncher.runtime

/** Selects only exact Java-major and current-process ABI matches; it never loads a runtime. */
internal object JavaRuntimeSelectionPolicy {
    fun findExactMajor(
        requiredJavaMajorVersion: Int?,
        candidates: List<AndroidJavaRuntimeManager.InstalledRuntime>
    ): AndroidJavaRuntimeManager.InstalledRuntime? {
        if (requiredJavaMajorVersion == null || requiredJavaMajorVersion < 1) return null
        return candidates.firstOrNull {
            it.matchesProcessAbi && it.javaMajorVersion == requiredJavaMajorVersion
        }
    }
}
