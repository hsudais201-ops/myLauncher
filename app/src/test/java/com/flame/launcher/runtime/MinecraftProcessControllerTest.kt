package com.flame.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MinecraftProcessControllerTest {
    @Test
    fun startsSessionRedactsAccessTokenAndRequestsGracefulStop() {
        val token = "private-test-token"
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val terminal = CountDownLatch(1)
        val stopRequested = AtomicBoolean(false)
        val events = CopyOnWriteArrayList<MinecraftProcessEvent>()
        val host = object : MinecraftProcessHost {
            override fun start(
                command: MinecraftLaunchCommand,
                onOutput: (String) -> Unit
            ): MinecraftRunningProcess {
                onOutput("session started with token $token")
                started.countDown()
                return object : MinecraftRunningProcess {
                    override fun awaitExit(): Int {
                        check(stopped.await(5, TimeUnit.SECONDS)) { "stop request was not delivered" }
                        return 143
                    }

                    override fun requestStop() {
                        stopRequested.set(true)
                        stopped.countDown()
                    }
                }
            }
        }
        val executor = Executors.newSingleThreadExecutor()
        val controller = MinecraftProcessController(host, executor)
        try {
            val command = MinecraftLaunchCommand(
                executable = File("/unused/java"),
                workingDirectory = File("/unused/game"),
                arguments = listOf("--accessToken", token),
                classPath = emptyList()
            )
            val id = controller.launch(command) { event ->
                events += event
                if (event is MinecraftProcessEvent.StateChanged &&
                    event.snapshot.state.isTerminalForTest()
                ) terminal.countDown()
            }

            assertTrue(started.await(5, TimeUnit.SECONDS))
            val output = events.filterIsInstance<MinecraftProcessEvent.Output>().single()
            assertFalse(output.line.contains(token))
            assertTrue(output.line.contains("[REDACTED]"))
            assertTrue(controller.stop(id))
            assertTrue(terminal.await(5, TimeUnit.SECONDS))
            assertTrue(stopRequested.get())
            val snapshot = controller.snapshot(id)
            assertNotNull(snapshot)
            assertEquals(MinecraftProcessState.EXITED, snapshot?.state)
            assertEquals(143, snapshot?.exitCode)
            assertFalse(controller.stop(id))
            assertTrue(controller.forget(id))
        } finally {
            controller.close()
        }
    }

    @Test
    fun stopBeforeWorkerStartsCancelsWithoutStartingHost() {
        val workerBlocked = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val terminal = CountDownLatch(1)
        val hostStarted = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            workerBlocked.countDown()
            check(releaseWorker.await(5, TimeUnit.SECONDS))
        }
        assertTrue(workerBlocked.await(5, TimeUnit.SECONDS))

        val host = object : MinecraftProcessHost {
            override fun start(
                command: MinecraftLaunchCommand,
                onOutput: (String) -> Unit
            ): MinecraftRunningProcess {
                hostStarted.set(true)
                return object : MinecraftRunningProcess {
                    override fun awaitExit(): Int = 0
                    override fun requestStop() = Unit
                }
            }
        }
        val controller = MinecraftProcessController(host, executor)
        try {
            val command = MinecraftLaunchCommand(
                executable = File("/unused/java"),
                workingDirectory = File("/unused/game"),
                arguments = emptyList(),
                classPath = emptyList()
            )
            val id = controller.launch(command) { event ->
                if (event is MinecraftProcessEvent.StateChanged &&
                    event.snapshot.state.isTerminalForTest()
                ) terminal.countDown()
            }

            assertTrue(controller.stop(id))
            releaseWorker.countDown()
            assertTrue(terminal.await(5, TimeUnit.SECONDS))
            assertFalse(hostStarted.get())
            assertEquals(MinecraftProcessState.CANCELLED, controller.snapshot(id)?.state)
        } finally {
            releaseWorker.countDown()
            controller.close()
        }
    }

    @Test
    fun startupFailureRedactsAccessTokenFromSessionError() {
        val token = "private-startup-token"
        val terminal = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val host = object : MinecraftProcessHost {
            override fun start(
                command: MinecraftLaunchCommand,
                onOutput: (String) -> Unit
            ): MinecraftRunningProcess {
                throw IllegalStateException("Native startup rejected token=$token")
            }
        }
        val controller = MinecraftProcessController(host, executor)
        try {
            val command = MinecraftLaunchCommand(
                executable = File("/unused/java"),
                workingDirectory = File("/unused/game"),
                arguments = listOf("--accessToken", token),
                classPath = emptyList()
            )
            val id = controller.launch(command) { event ->
                if (event is MinecraftProcessEvent.StateChanged &&
                    event.snapshot.state.isTerminalForTest()
                ) terminal.countDown()
            }

            assertTrue(terminal.await(5, TimeUnit.SECONDS))
            val snapshot = controller.snapshot(id)
            assertEquals(MinecraftProcessState.FAILED, snapshot?.state)
            assertFalse(snapshot?.error.orEmpty().contains(token))
            assertTrue(snapshot?.error.orEmpty().contains("[REDACTED]"))
        } finally {
            controller.close()
        }
    }

    private fun MinecraftProcessState.isTerminalForTest(): Boolean =
        this == MinecraftProcessState.EXITED ||
            this == MinecraftProcessState.CANCELLED ||
            this == MinecraftProcessState.FAILED
}
