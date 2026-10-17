package com.mf.mylauncher.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs a prepared Java runtime without a shell, drains both output streams and supports cancellation.
 * This is process infrastructure only; callers must provide an Android-compatible runtime and valid login.
 */
public final class JavaProcessRunner {
    public static final long MAX_TIMEOUT_MILLIS = 7L * 24L * 60L * 60L * 1000L;
    private static final int MAX_LOG_LINE_CHARS = 16 * 1024;
    private static final long PROCESS_TERMINATION_GRACE_MILLIS = 500;
    private static final long OUTPUT_DRAIN_TIMEOUT_MILLIS = 2_000;

    public Result run(Path javaExecutable, Path workingDirectory, List<String> arguments,
                      CancellationToken cancellation, long timeoutMillis,
                      OutputListener outputListener) throws IOException, InterruptedException {
        if (javaExecutable == null) throw new IllegalArgumentException("Java executable path is required");
        if (workingDirectory == null) throw new IllegalArgumentException("Java working directory is required");
        if (arguments == null) throw new IllegalArgumentException("Java arguments are required");
        if (cancellation == null) throw new IllegalArgumentException("Cancellation token is required");
        if (timeoutMillis <= 0 || timeoutMillis > MAX_TIMEOUT_MILLIS) {
            throw new IllegalArgumentException("Process timeout must be between 1 millisecond and 7 days");
        }
        checkCancelled(cancellation);

        Path requestedExecutable = javaExecutable.toAbsolutePath().normalize();
        Path current = requestedExecutable.getRoot();
        for (Path segment : requestedExecutable) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Java executable path must not contain symbolic links");
            }
        }

        Path executable;
        try {
            executable = requestedExecutable.toRealPath();
        } catch (IOException exception) {
            throw new IOException("Java executable could not be resolved", exception);
        }
        if (!Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS)
                || !Files.isExecutable(executable)) {
            throw new IOException("Java executable must be a regular executable file");
        }

        Path directory = workingDirectory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Java working directory must be an existing non-symlink directory");
        }

        List<String> command = new ArrayList<>(arguments.size() + 1);
        command.add(executable.toString());
        for (String argument : arguments) {
            if (argument == null || argument.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Java argv must not contain null values or NUL characters");
            }
            command.add(argument);
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(directory.toFile());
        Map<String, String> environment = builder.environment();
        environment.remove("JAVA_TOOL_OPTIONS");
        environment.remove("_JAVA_OPTIONS");
        environment.remove("JDK_JAVA_OPTIONS");
        environment.remove("CLASSPATH");

        long startNanos = System.nanoTime();
        Process process = builder.start();
        AtomicInteger stdoutLines = new AtomicInteger();
        AtomicInteger stderrLines = new AtomicInteger();
        ExecutorService readers = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "myLauncher-process-output");
            thread.setDaemon(true);
            return thread;
        });
        Future<?> stdout = readers.submit(() -> pump(process.getInputStream(), Stream.STDOUT,
                outputListener, stdoutLines));
        Future<?> stderr = readers.submit(() -> pump(process.getErrorStream(), Stream.STDERR,
                outputListener, stderrLines));

        boolean finishedNormally = false;
        try {
            long deadline = startNanos + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (true) {
                checkCancelled(cancellation);
                if (process.waitFor(100, TimeUnit.MILLISECONDS)) {
                    finishedNormally = true;
                    break;
                }
                if (System.nanoTime() - deadline >= 0) {
                    throw new IOException("Java process exceeded its time limit");
                }
            }
            awaitOutputDrain(stdout);
            awaitOutputDrain(stderr);
            return new Result(process.exitValue(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
                    stdoutLines.get(), stderrLines.get());
        } finally {
            if (!finishedNormally || process.isAlive()) terminate(process);
            closeQuietly(process.getInputStream());
            closeQuietly(process.getErrorStream());
            closeQuietly(process.getOutputStream());
            readers.shutdownNow();
        }
    }

    private static void checkCancelled(CancellationToken cancellation) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || cancellation.isCancelled()) {
            throw new InterruptedIOException("Java process was cancelled");
        }
    }

    private static void terminate(Process process) {
        if (!process.isAlive()) return;
        process.destroy();
        try {
            if (!process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static void pump(InputStream stream, Stream kind, OutputListener listener, AtomicInteger count) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder line = new StringBuilder();
            boolean truncated = false;
            int next;
            while ((next = reader.read()) != -1) {
                char ch = (char) next;
                if (ch == '\n') {
                    emitLine(kind, line, truncated, listener, count);
                    line.setLength(0);
                    truncated = false;
                } else if (ch != '\r') {
                    if (line.length() < MAX_LOG_LINE_CHARS) line.append(ch);
                    else truncated = true;
                }
            }
            if (line.length() > 0 || truncated) emitLine(kind, line, truncated, listener, count);
        } catch (IOException exception) {
            // A killed or cancelled process may close a stream mid-line; it must not keep the child alive.
        }
    }

    private static void emitLine(Stream kind, StringBuilder line, boolean truncated,
                                 OutputListener listener, AtomicInteger count) {
        count.incrementAndGet();
        if (listener == null) return;
        String value = line.toString() + (truncated ? " …[line truncated]" : "");
        try {
            listener.onLine(kind, value);
        } catch (RuntimeException ignored) {
            // Output observers must not stop draining a pipe and deadlock the child process.
        }
    }

    private static void awaitOutputDrain(Future<?> future) throws IOException, InterruptedException {
        try {
            future.get(OUTPUT_DRAIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException exception) {
            throw new IOException("Could not drain Java process output", exception.getCause());
        } catch (TimeoutException exception) {
            throw new IOException("Java process output did not close after the process exited", exception);
        }
    }

    private static void closeQuietly(AutoCloseable stream) {
        try { stream.close(); } catch (Exception ignored) { }
    }

    public enum Stream { STDOUT, STDERR }

    @FunctionalInterface
    public interface OutputListener {
        void onLine(Stream stream, String line);
    }

    public static final class Result {
        private final int exitCode;
        private final long elapsedMillis;
        private final int stdoutLineCount;
        private final int stderrLineCount;

        private Result(int exitCode, long elapsedMillis, int stdoutLineCount, int stderrLineCount) {
            this.exitCode = exitCode;
            this.elapsedMillis = elapsedMillis;
            this.stdoutLineCount = stdoutLineCount;
            this.stderrLineCount = stderrLineCount;
        }
        public int getExitCode() { return exitCode; }
        public long getElapsedMillis() { return elapsedMillis; }
        public int getStdoutLineCount() { return stdoutLineCount; }
        public int getStderrLineCount() { return stderrLineCount; }
        public boolean isSuccess() { return exitCode == 0; }
    }
}
