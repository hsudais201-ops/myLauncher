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
        return bridge.start(
            jvmLibrary = jvmLibrary,
            runtimeHome = runtimeHome,
            workingDirectory = command.workingDirectory.canonicalFile,
            arguments = command.arguments.toList(),
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
    fun start(
        jvmLibrary: File,
        runtimeHome: File,
        workingDirectory: File,
        arguments: List<String>,
        onOutput: (String) -> Unit
    ): AndroidJvmSession
}

/** Handle returned by the native embedded JVM bridge. */
interface AndroidJvmSession : MinecraftRunningProcess
