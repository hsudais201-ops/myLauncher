package com.flame.launcher.runtime

import java.io.File

/**
 * Native preflight for an extracted Android JVM library.
 *
 * This verifies that Android's dynamic linker can load the selected libjvm
 * and that the JNI invocation entry points are exported. It does NOT create a
 * JVM or launch Minecraft; that remains a separate native integration step.
 *
 * Call [inspect] from a worker thread because dlopen may resolve dependencies.
 */
class NativeJvmLibraryProbe internal constructor(
    private val nativeInspector: ((String) -> String)? = null
) {
    init {
        if (nativeInspector == null) {
            System.loadLibrary(NATIVE_LIBRARY_NAME)
        }
    }

    fun inspect(runtime: JavaRuntimeInstallation): NativeJvmLibraryProbeResult {
        require(runtime.executionKind == JavaRuntimeExecutionKind.EMBEDDED_JVM) {
            "Selected runtime is not an embedded JVM package."
        }
        val home = runtime.homeDirectory.canonicalFile
        val library = runtime.jvmLibrary?.canonicalFile
            ?: return NativeJvmLibraryProbeResult(false, "Runtime does not contain libjvm.so.")
        require(home.isDirectory && library.isFile &&
            library.path.startsWith(home.path + File.separator)
        ) { "JVM library must be a file inside the selected runtime home." }

        val diagnostic = nativeInspector?.invoke(library.absolutePath)
            ?: nativeInspectJvmLibrary(library.absolutePath)
        return NativeJvmLibraryProbeResult(
            available = diagnostic.startsWith("OK:"),
            diagnostic = diagnostic
        )
    }

    private external fun nativeInspectJvmLibrary(absolutePath: String): String

    companion object {
        private const val NATIVE_LIBRARY_NAME = "flame_jvm_bridge"
    }
}

data class NativeJvmLibraryProbeResult(
    val available: Boolean,
    val diagnostic: String
)
