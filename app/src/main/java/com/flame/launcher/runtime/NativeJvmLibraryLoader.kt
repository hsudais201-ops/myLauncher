package com.flame.launcher.runtime

import java.io.File

/**
 * Loads libjvm into Android's process linker namespace and deliberately retains
 * its handle for the lifetime of the process. This is a loader stage only: it
 * does not create a JavaVM or invoke Minecraft's main class.
 *
 * A process can retain only one selected embedded JVM. Do not add an unload
 * operation; closing this handle while JVM-owned threads or native methods are
 * active can crash the Android application.
 */
class NativeJvmLibraryLoader internal constructor(
    private val nativeRetainer: ((String) -> String)? = null,
    private val nativeVmInspector: (() -> String)? = null
) {
    init {
        if (nativeRetainer == null && nativeVmInspector == null) {
            System.loadLibrary(NATIVE_LIBRARY_NAME)
        }
    }

    fun retain(runtime: JavaRuntimeInstallation): NativeJvmLibraryLoadResult {
        require(runtime.executionKind == JavaRuntimeExecutionKind.EMBEDDED_JVM) {
            "Selected runtime is not an embedded JVM package."
        }
        val home = runtime.homeDirectory.canonicalFile
        val library = runtime.jvmLibrary?.canonicalFile
            ?: return NativeJvmLibraryLoadResult(false, "Runtime does not contain libjvm.so.")
        require(home.isDirectory && library.isFile &&
            library.path.startsWith(home.path + File.separator)
        ) { "JVM library must be a file inside the selected runtime home." }

        val diagnostic = nativeRetainer?.invoke(library.absolutePath)
            ?: nativeRetainJvmLibrary(library.absolutePath)
        return NativeJvmLibraryLoadResult(
            retained = diagnostic.startsWith("OK:"),
            diagnostic = diagnostic
        )
    }

    /**
     * Queries the retained OpenJDK library's JNI_GetCreatedJavaVMs entry point.
     * This does not inspect Android ART and does not create or destroy a VM.
     */
    fun inspectVmState(): String = nativeVmInspector?.invoke() ?: nativeInspectVmState()

    private external fun nativeRetainJvmLibrary(absolutePath: String): String
    private external fun nativeInspectVmState(): String

    companion object {
        private const val NATIVE_LIBRARY_NAME = "flame_jvm_bridge"
    }
}

data class NativeJvmLibraryLoadResult(
    val retained: Boolean,
    val diagnostic: String
)
