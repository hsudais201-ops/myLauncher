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
    private val nativeVmInspector: (() -> String)? = null,
    private val nativeRuntimeRetainer: ((String, Array<String>) -> String)? = null,
    private val nativeRetainer: ((String) -> String)? = null
) {
    init {
        if ((nativeRetainer == null && nativeRuntimeRetainer == null) || nativeVmInspector == null) {
            System.loadLibrary(NATIVE_LIBRARY_NAME)
        }
    }

    fun retain(
        runtime: JavaRuntimeInstallation,
        runtimeDependencies: List<File> = emptyList()
    ): NativeJvmLibraryLoadResult {
        require(runtime.executionKind == JavaRuntimeExecutionKind.EMBEDDED_JVM) {
            "Selected runtime is not an embedded JVM package."
        }
        val home = runtime.homeDirectory.canonicalFile
        val library = runtime.jvmLibrary?.canonicalFile
            ?: return NativeJvmLibraryLoadResult(false, "Runtime does not contain libjvm.so.")
        require(home.isDirectory && library.isFile &&
            library.path.startsWith(home.path + File.separator)
        ) { "JVM library must be a file inside the selected runtime home." }

        val dependencies = runtimeDependencies.map { it.canonicalFile }.distinct()
        require(dependencies.all { dependency ->
            dependency.isFile && dependency != library &&
                dependency.path.startsWith(home.path + File.separator)
        }) { "Every preloaded JVM dependency must be a file inside the selected runtime home." }
        val diagnostic = if (dependencies.isEmpty()) {
            nativeRetainer?.invoke(library.absolutePath)
                ?: nativeRetainJvmLibrary(library.absolutePath)
        } else {
            val paths = dependencies.map { it.absolutePath }.toTypedArray()
            when {
                nativeRuntimeRetainer != null ->
                    nativeRuntimeRetainer.invoke(library.absolutePath, paths)
                nativeRetainer != null ->
                    "ERROR: Native dependency preloading is unavailable in this injected loader."
                else -> nativeRetainJvmRuntime(library.absolutePath, paths)
            }
        }
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
    private external fun nativeRetainJvmRuntime(absolutePath: String, dependencyPaths: Array<String>): String
    private external fun nativeInspectVmState(): String

    companion object {
        private const val NATIVE_LIBRARY_NAME = "flame_jvm_bridge"
    }
}

data class NativeJvmLibraryLoadResult(
    val retained: Boolean,
    val diagnostic: String
)
