package com.flame.launcher.runtime

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns one Minecraft launch session from host startup through process exit.
 *
 * The host is deliberately injected: Android Minecraft must be started by a
 * compatible in-process JVM/native bridge, not by assuming that a desktop
 * java executable can be spawned from app-private storage.
 *
 * Never log [MinecraftLaunchCommand.arguments]. They contain authentication
 * material. Output is also scrubbed for the access token before observers see it.
 */
class MinecraftProcessController(
    private val host: MinecraftProcessHost,
    private val executor: ExecutorService = Executors.newCachedThreadPool()
) : AutoCloseable {
    private val sessions = ConcurrentHashMap<String, Session>()
    private val closed = AtomicBoolean(false)

    fun launch(
        command: MinecraftLaunchCommand,
        onEvent: (MinecraftProcessEvent) -> Unit = {}
    ): String {
        check(!closed.get()) { "Minecraft process controller is closed." }
        val id = UUID.randomUUID().toString()
        val session = Session(id, command.accessToken(), onEvent)
        check(sessions.putIfAbsent(id, session) == null)
        try {
            executor.execute { runSession(session, command) }
        } catch (failure: RuntimeException) {
            sessions.remove(id)
            throw failure
        }
        return id
    }

    fun snapshot(sessionId: String): MinecraftProcessSnapshot? =
        sessions[sessionId]?.snapshot()

    /**
     * Requests a graceful stop. The host decides how its JVM bridge delivers
     * the stop request; this method never blocks the UI thread.
     */
    fun stop(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        synchronized(session) {
            if (session.state.isTerminal()) return false
            session.stopRequested.set(true)
            session.state = MinecraftProcessState.STOPPING
            publish(session, MinecraftProcessEvent.StateChanged(session.snapshot()))
            session.process?.let { requestStopSafely(session, it) }
        }
        return true
    }

    fun forget(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        synchronized(session) {
            if (!session.state.isTerminal()) return false
            return sessions.remove(sessionId, session)
        }
    }

    private fun runSession(session: Session, command: MinecraftLaunchCommand) {
        try {
            if (!beginStarting(session)) return
            val process = host.start(command) { line ->
                publish(session, MinecraftProcessEvent.Output(session.id, redact(line, session.accessToken)))
            }
            synchronized(session) {
                session.process = process
                if (session.stopRequested.get()) {
                    session.state = MinecraftProcessState.STOPPING
                    publish(session, MinecraftProcessEvent.StateChanged(session.snapshot()))
                    requestStopSafely(session, process)
                } else {
                    session.state = MinecraftProcessState.RUNNING
                    publish(session, MinecraftProcessEvent.StateChanged(session.snapshot()))
                }
            }
            val exitCode = process.awaitExit()
            transition(session, MinecraftProcessState.EXITED, exitCode = exitCode)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            transition(session, MinecraftProcessState.CANCELLED)
        } catch (failure: Exception) {
            failSession(session, failure)
        } catch (failure: LinkageError) {
            // Native bridge loading can fail with UnsatisfiedLinkError. Record
            // that as a failed launch, but do not swallow fatal VM errors such
            // as OutOfMemoryError or ThreadDeath.
            failSession(session, failure)
        } finally {
            synchronized(session) { session.process = null }
        }
    }

    /**
     * Atomically checks cancellation and publishes STARTING under the same
     * session lock used by stop(). This prevents a concurrent stop request
     * from being overwritten by a late STARTING transition.
     */
    private fun beginStarting(session: Session): Boolean {
        synchronized(session) {
            if (session.state.isTerminal()) return false
            if (session.stopRequested.get()) {
                session.state = MinecraftProcessState.CANCELLED
                publish(session, MinecraftProcessEvent.StateChanged(session.snapshot()))
                return false
            }
            session.state = MinecraftProcessState.STARTING
            publish(session, MinecraftProcessEvent.StateChanged(session.snapshot()))
            return true
        }
    }

    private fun requestStopSafely(session: Session, process: MinecraftRunningProcess) {
        try {
            process.requestStop()
        } catch (failure: Exception) {
            failSession(session, failure)
        } catch (failure: LinkageError) {
            // A missing/broken native stop entry point is a session failure,
            // but fatal VM errors must still propagate.
            failSession(session, failure)
        }
    }

    private fun failSession(session: Session, failure: Throwable) {
        transition(
            session,
            MinecraftProcessState.FAILED,
            error = redact(
                failure.message?.takeIf(String::isNotBlank) ?: "Minecraft runtime failed.",
                session.accessToken
            ).take(MAX_ERROR_LENGTH)
        )
    }

    private fun transition(
        session: Session,
        state: MinecraftProcessState,
        exitCode: Int? = null,
        error: String? = null
    ) {
        synchronized(session) {
            // Once a session reaches a terminal state, a late worker result
            // must not overwrite the outcome (for example, EXITED after a
            // stop failure has already marked the session FAILED).
            if (session.state.isTerminal()) return
            session.state = state
            session.exitCode = exitCode
            session.error = error
            publish(session, MinecraftProcessEvent.StateChanged(session.snapshot()))
        }
    }

    private fun publish(session: Session, event: MinecraftProcessEvent) {
        try {
            session.observer(event)
        } catch (_: Exception) {
            // A UI observer must never terminate the Minecraft process.
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        sessions.values.forEach { session ->
            synchronized(session) {
                if (!session.state.isTerminal()) {
                    session.stopRequested.set(true)
                    session.state = MinecraftProcessState.STOPPING
                    publish(session, MinecraftProcessEvent.StateChanged(session.snapshot()))
                    session.process?.let { requestStopSafely(session, it) }
                }
            }
        }
        executor.shutdown()
    }

    private class Session(
        val id: String,
        val accessToken: String,
        val observer: (MinecraftProcessEvent) -> Unit
    ) {
        var state: MinecraftProcessState = MinecraftProcessState.PREPARING
        var exitCode: Int? = null
        var error: String? = null
        var process: MinecraftRunningProcess? = null
        val stopRequested = AtomicBoolean(false)

        @Synchronized
        fun snapshot() = MinecraftProcessSnapshot(id, state, exitCode, error)
    }

    private fun MinecraftLaunchCommand.accessToken(): String {
        val index = arguments.indexOf("--accessToken")
        return arguments.getOrNull(index + 1).orEmpty()
    }

    private fun MinecraftProcessState.isTerminal(): Boolean =
        this == MinecraftProcessState.EXITED ||
            this == MinecraftProcessState.CANCELLED ||
            this == MinecraftProcessState.FAILED

    private fun redact(value: String, token: String): String {
        if (token.isBlank()) return value
        return value.replace(token, REDACTED)
    }

    companion object {
        private const val MAX_ERROR_LENGTH = 300
        private const val REDACTED = "[REDACTED]"
    }
}

/**
 * Implement this with the Android-compatible JVM bridge. Do not implement it
 * with ProcessBuilder unless the target runtime is proven executable on-device.
 */
interface MinecraftProcessHost {
    fun start(
        command: MinecraftLaunchCommand,
        onOutput: (String) -> Unit
    ): MinecraftRunningProcess
}

/** A running JVM session controlled by [MinecraftProcessController]. */
interface MinecraftRunningProcess {
    /** Blocks on the worker thread until the JVM exits and returns its exit code. */
    fun awaitExit(): Int

    /** Requests graceful JVM shutdown and must return promptly. */
    fun requestStop()
}

enum class MinecraftProcessState {
    PREPARING,
    STARTING,
    RUNNING,
    STOPPING,
    EXITED,
    CANCELLED,
    FAILED
}

data class MinecraftProcessSnapshot(
    val sessionId: String,
    val state: MinecraftProcessState,
    val exitCode: Int? = null,
    val error: String? = null
)

sealed interface MinecraftProcessEvent {
    data class StateChanged(val snapshot: MinecraftProcessSnapshot) : MinecraftProcessEvent
    data class Output(val sessionId: String, val line: String) : MinecraftProcessEvent
}
