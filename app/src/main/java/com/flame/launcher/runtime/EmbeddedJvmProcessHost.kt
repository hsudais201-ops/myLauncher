package com.flame.launcher.runtime

import java.io.File

/**
 * Adapts an Android in-process JVM implementation to the launch-session API.
 *
 * The bridge implementation is supplied by the native runtime integration.
 * This class intentionally does not call ProcessBuilder or System.exit: an
 * embedded game shares the Android process and must be stopped through its
 * dedicated JVM/session handle.
 */
class EmbeddedJvmProcessHost(
    private val bridge: AndroidJvmBridge
) : MinecraftProcessHost {
    override fun start(
        command: MinecraftLaunchCommand,
        onOutput: (String) -> Unit
    ): MinecraftRunningProcess {
        require(command.executionKind == JavaRuntimeExecutionKind.EMBEDDED_JVM) {
            "Embedded JVM host received a process-based Java runtime."
        }
        // Fail closed: a bridge must explicitly prove its JNI invocation path
        // is safe alongside Android ART before any native VM creation attempt.
        val readiness = bridge.invocationReadiness
        check(readiness.mayInvokeCreateJavaVm) {
            when (readiness) {
                is JvmInvocationReadiness.Blocked -> readiness.diagnostic
                is JvmInvocationReadiness.Unverified -> readiness.diagnostic
                is JvmInvocationReadiness.Failed -> readiness.diagnostic
                JvmInvocationReadiness.Ready -> "Unexpected JVM readiness state."
            }
        }
        val runtimeHome = command.runtimeHome?.canonicalFile
            ?: throw IllegalArgumentException("Embedded JVM runtime home is missing.")
        val jvmLibrary = command.jvmLibrary?.canonicalFile
            ?: throw IllegalArgumentException("Embedded JVM shared library is missing.")
        require(runtimeHome.isDirectory) { "Embedded JVM runtime home is unavailable." }
        require(jvmLibrary.isFile &&
            jvmLibrary.path.startsWith(runtimeHome.path + File.separator)
        ) { "Embedded JVM shared library is outside its runtime home." }
        require(command.workingDirectory.isDirectory) {
            "Minecraft game directory is unavailable."
        }
        val workingDirectory = command.workingDirectory.canonicalFile
        val invocation = EmbeddedJvmInvocationParser.parse(
            arguments = command.arguments,
            expectedClassPath = command.classPath,
            runtimeHome = runtimeHome,
            workingDirectory = workingDirectory
        )
        val nativeDirectory = resolveApplicationNativeDirectory(
            invocation.vmOptions,
            workingDirectory
        )
        val runtimeLayout = EmbeddedJvmRuntimeLayoutResolver.resolve(
            runtimeHome = runtimeHome,
            jvmLibrary = jvmLibrary,
            applicationNativeDirectory = nativeDirectory
        )
        // Validate and freeze the exact JNI_CreateJavaVM option vector before
        // crossing the native boundary. The bridge receives this separately
        // from the Java main-class invocation to prevent option reparsing.
        val createPlan = invocation.toVmCreationPlan()
        return bridge.start(
            runtimeLayout = runtimeLayout,
            createPlan = createPlan,
            workingDirectory = workingDirectory,
            invocation = invocation,
            onOutput = onOutput
        )
    }
}

/**
 * Narrow contract implemented by the platform's native JVM invocation bridge.
 *
 * A production implementation must attach/detach the calling native thread,
 * preserve JNI references safely, forward stdout/stderr without leaking secrets,
 * and request shutdown without terminating the Android application process.
 */
interface AndroidJvmBridge {
    /**
     * Implementations remain disabled until ART coexistence and the complete
     * native VM lifecycle have been verified on supported physical devices.
     * The safe default prevents an incomplete bridge from starting a JVM.
     */
    val invocationReadiness: JvmInvocationReadiness
        get() = JvmInvocationReadiness.Unverified(
            "Embedded JVM invocation is not verified for Android ART coexistence."
        )

    fun start(
        runtimeLayout: EmbeddedJvmRuntimeLayout,
        createPlan: EmbeddedJvmCreatePlan,
        workingDirectory: File,
        invocation: EmbeddedJvmInvocation,
        onOutput: (String) -> Unit
    ): AndroidJvmSession
}

/** Handle returned by the native embedded JVM bridge. */
interface AndroidJvmSession : MinecraftRunningProcess


private fun resolveApplicationNativeDirectory(
    vmOptions: List<String>,
    workingDirectory: File
): File? {
    val javaPaths = vmOptions.filter { it.startsWith("-Djava.library.path=") }
        .map { it.substringAfter('=') }
    val lwjglPaths = vmOptions.filter { it.startsWith("-Dorg.lwjgl.librarypath=") }
        .map { it.substringAfter('=') }
    require(javaPaths.size <= 1 && lwjglPaths.size <= 1) {
        "Embedded JVM native-library paths must not be duplicated."
    }
    val configuredPaths = listOfNotNull(javaPaths.singleOrNull(), lwjglPaths.singleOrNull())
    require(configuredPaths.distinct().size <= 1) {
        "Java and LWJGL native-library paths must match."
    }
    val configured = configuredPaths.firstOrNull() ?: return null
    val directory = File(configured)
    val canonicalDirectory = directory.canonicalFile
    require(directory.isAbsolute && canonicalDirectory.isDirectory &&
        canonicalDirectory.path.startsWith(workingDirectory.canonicalPath + File.separator)
    ) {
        "Minecraft native-library directory must be an existing directory inside the game directory."
    }
    return canonicalDirectory
}
