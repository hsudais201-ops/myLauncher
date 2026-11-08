package com.flame.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

class RoutingMinecraftProcessHostTest {
    @Test
    fun routesEmbeddedAndProcessRuntimesToTheirMatchingHosts() {
        val embeddedSession = FakeSession(17)
        val processSession = FakeSession(23)
        val embedded = RecordingHost(embeddedSession)
        val process = RecordingHost(processSession)
        val router = RoutingMinecraftProcessHost(embedded, process)

        val embeddedCommand = command(JavaRuntimeExecutionKind.EMBEDDED_JVM)
        val processCommand = command(JavaRuntimeExecutionKind.PROCESS)

        assertSame(embeddedSession, router.start(embeddedCommand) { })
        assertSame(processSession, router.start(processCommand) { })
        assertEquals(1, embedded.calls)
        assertEquals(1, process.calls)
    }

    private fun command(kind: JavaRuntimeExecutionKind) = MinecraftLaunchCommand(
        executable = if (kind == JavaRuntimeExecutionKind.PROCESS) File("/unused/java") else null,
        workingDirectory = File("/unused/game"),
        arguments = emptyList(),
        classPath = emptyList(),
        runtimeHome = File("/unused/runtime"),
        jvmLibrary = if (kind == JavaRuntimeExecutionKind.EMBEDDED_JVM) {
            File("/unused/runtime/lib/server/libjvm.so")
        } else null,
        executionKind = kind
    )

    private class RecordingHost(
        private val session: MinecraftRunningProcess
    ) : MinecraftProcessHost {
        var calls = 0
        override fun start(
            command: MinecraftLaunchCommand,
            onOutput: (String) -> Unit
        ): MinecraftRunningProcess {
            calls++
            return session
        }
    }

    private class FakeSession(private val result: Int) : MinecraftRunningProcess {
        override fun awaitExit(): Int = result
        override fun requestStop() = Unit
    }
}
