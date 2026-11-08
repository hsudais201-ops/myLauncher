package com.flame.launcher.runtime

/**
 * Selects the runtime host from the validated Java installation kind.
 *
 * Android Mojo runtimes with libjvm.so must use the embedded bridge. A
 * process-based runtime is delegated to a separately supplied host, allowing
 * device policy/tests to reject desktop executables rather than silently
 * treating them as Android-compatible.
 */
class RoutingMinecraftProcessHost(
    private val embeddedHost: MinecraftProcessHost,
    private val processHost: MinecraftProcessHost
) : MinecraftProcessHost {
    override fun start(
        command: MinecraftLaunchCommand,
        onOutput: (String) -> Unit
    ): MinecraftRunningProcess = when (command.executionKind) {
        JavaRuntimeExecutionKind.EMBEDDED_JVM -> embeddedHost.start(command, onOutput)
        JavaRuntimeExecutionKind.PROCESS -> processHost.start(command, onOutput)
    }
}
