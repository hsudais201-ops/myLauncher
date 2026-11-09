package com.flame.launcher.runtime

import java.io.File

/**
 * Performs the ordered, non-launching preparation steps for an embedded JVM.
 *
 * Run on a worker thread: the native probe and retained dlopen may resolve
 * runtime dependencies. A successful result means the linker accepted the
 * selected library and the process retained its handle. It does not mean that
 * JNI_CreateJavaVM succeeded or that Minecraft can run.
 */
class NativeJvmRuntimePreparer(
    private val probe: NativeJvmLibraryProbe,
    private val loader: NativeJvmLibraryLoader,
    private val dependencyResolver: JvmNativeDependencyResolver = JvmNativeDependencyResolver()
) {
    fun prepare(
        runtime: JavaRuntimeInstallation,
        applicationNativeDirectory: File? = null
    ): NativeJvmRuntimePreparationResult {
        require(runtime.executionKind == JavaRuntimeExecutionKind.EMBEDDED_JVM) {
            "Selected runtime is not an embedded JVM package."
        }
        val home = runtime.homeDirectory.canonicalFile
        val library = runtime.jvmLibrary?.canonicalFile
            ?: return NativeJvmRuntimePreparationResult.Failed(
                "Selected runtime does not contain libjvm.so."
            )
        val layout = EmbeddedJvmRuntimeLayoutResolver.resolve(
            runtimeHome = home,
            jvmLibrary = library,
            applicationNativeDirectory = applicationNativeDirectory
        )

        val dependencyResolution = dependencyResolver.resolve(
            rootLibrary = library,
            runtimeHome = home,
            // Minecraft's extracted natives are not JVM runtime files. The
            // resolver deliberately confines DT_NEEDED resolution to the
            // selected JRE tree, even when the launch layout also includes
            // an application-native directory for LWJGL.
            searchDirectories = layout.libraryDirectories.filter {
                it.canonicalFile.path.startsWith(home.path + File.separator)
            }
        )
        val runtimeLoadOrder = when (dependencyResolution) {
            is JvmNativeDependencyResolution.Resolved -> dependencyResolution.runtimeLoadOrder
            is JvmNativeDependencyResolution.Unresolved ->
                return NativeJvmRuntimePreparationResult.Failed(
                    "JVM dependency ${dependencyResolution.library} requested by " +
                        "${dependencyResolution.requestedBy.name} was not found in approved runtime directories."
                )
            is JvmNativeDependencyResolution.Rejected ->
                return NativeJvmRuntimePreparationResult.Failed(
                    "JVM dependency metadata was rejected: ${dependencyResolution.diagnostic}"
                )
        }

        val preflight = probe.inspect(runtime, runtimeLoadOrder)
        if (!preflight.available) {
            return NativeJvmRuntimePreparationResult.Failed(
                "JVM linker preflight failed: ${preflight.diagnostic}"
            )
        }

        val retained = loader.retain(runtime, runtimeLoadOrder)
        if (!retained.retained) {
            return NativeJvmRuntimePreparationResult.Failed(
                "JVM handle retention failed: ${retained.diagnostic}"
            )
        }

        return NativeJvmRuntimePreparationResult.Ready(
            runtimeHome = layout.runtimeHome,
            jvmLibrary = layout.jvmLibrary,
            libraryDirectories = layout.libraryDirectories,
            optionalPreloadLibraries = layout.optionalPreloadLibraries,
            diagnostic = retained.diagnostic
        )
    }
}

sealed interface NativeJvmRuntimePreparationResult {
    data class Ready(
        val runtimeHome: File,
        val jvmLibrary: File,
        val libraryDirectories: List<File>,
        val optionalPreloadLibraries: List<File>,
        val diagnostic: String
    ) : NativeJvmRuntimePreparationResult

    data class Failed(val diagnostic: String) : NativeJvmRuntimePreparationResult
}
