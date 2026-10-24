package com.mf.mylauncher.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Discovers Android-private Java runtimes and probes their real reported Java major version.
 *
 * <p>This is discovery and compatibility selection only. It deliberately does not download or
 * unpack executable runtime archives; the playable Amethyst engine owns its supported runtime
 * provider and native/JVM integration.</p>
 */
public final class JavaRuntimeCatalog {
    private static final Pattern RUNTIME_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}");
    private static final List<String> CONVENTIONAL_IDS = Collections.unmodifiableList(Arrays.asList(
            "java-8", "java-17", "java-21", "java-25", "default"));

    private JavaRuntimeCatalog() { }

    /**
     * Lists conventional slots and valid IDs found under <code>filesRoot/runtimes</code>.
     * Runtime executables are invoked only from the app-private, non-symlink location validated
     * by {@link JavaRuntimeLocator}. A failed probe is reported as unavailable rather than treated
     * as a compatible runtime.
     */
    public static List<Candidate> discover(Path filesRoot, Path workingDirectory,
                                           CancellationToken cancellation, long timeoutMillis)
            throws IOException, InterruptedException {
        if (filesRoot == null) throw new IllegalArgumentException("Private files root is required");
        if (workingDirectory == null) throw new IllegalArgumentException("Probe working directory is required");
        if (cancellation == null) throw new IllegalArgumentException("Cancellation token is required");
        if (timeoutMillis <= 0 || timeoutMillis > JavaProcessRunner.MAX_TIMEOUT_MILLIS) {
            throw new IllegalArgumentException("Probe timeout must be between 1 millisecond and 7 days");
        }
        checkCancelled(cancellation);

        Path privateRoot = filesRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(privateRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(privateRoot)) {
            throw new IOException("Private files root must be an existing non-symlink directory");
        }
        Path runtimesRoot = privateRoot.resolve("runtimes");
        if (Files.exists(runtimesRoot, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(runtimesRoot)
                    || !Files.isDirectory(runtimesRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Runtimes root must be a non-symlink directory");
            }
        }

        Set<String> ids = new LinkedHashSet<>(CONVENTIONAL_IDS);
        if (Files.isDirectory(runtimesRoot, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(runtimesRoot)) {
                for (Path child : children) {
                    checkCancelled(cancellation);
                    String id = child.getFileName().toString();
                    if ((Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(child))
                            && RUNTIME_ID.matcher(id).matches() && !id.contains("..")) ids.add(id);
                }
            }
        }

        JavaRuntimeLocator locator = new JavaRuntimeLocator(privateRoot);
        JavaProcessRunner runner = new JavaProcessRunner();
        List<Candidate> candidates = new ArrayList<>();
        for (String id : ids) {
            checkCancelled(cancellation);
            candidates.add(inspect(id, locator, runner, workingDirectory.toAbsolutePath().normalize(),
                    cancellation, timeoutMillis));
        }
        candidates.sort(Comparator.comparing(Candidate::getRuntimeId));
        return Collections.unmodifiableList(candidates);
    }

    /** Returns the best already-installed runtime with an exact Java major-version match. */
    public static Optional<Candidate> selectFor(List<Candidate> candidates, int requiredMajorVersion) {
        if (candidates == null) throw new IllegalArgumentException("Runtime candidates are required");
        if (requiredMajorVersion < 8 || requiredMajorVersion > 99) {
            throw new IllegalArgumentException("Required Java major version is outside the supported range");
        }
        return candidates.stream()
                .filter(Candidate::isAvailable)
                .filter(candidate -> candidate.getJavaMajorVersion() == requiredMajorVersion)
                .sorted(Comparator
                        .comparing((Candidate candidate) ->
                                !candidate.getRuntimeId().equals("java-" + requiredMajorVersion))
                        .thenComparing(candidate -> !candidate.getRuntimeId().equals("default"))
                        .thenComparing(Candidate::getRuntimeId))
                .findFirst();
    }

    private static Candidate inspect(String id, JavaRuntimeLocator locator, JavaProcessRunner runner,
                                     Path workingDirectory, CancellationToken cancellation, long timeoutMillis)
            throws IOException, InterruptedException {
        try {
            Optional<Path> executable = locator.locate(id);
            if (!executable.isPresent()) {
                return Candidate.missing(id, expectedMajorFromId(id));
            }
            List<String> output = Collections.synchronizedList(new ArrayList<>());
            JavaProcessRunner.Result result = runner.run(executable.get(), workingDirectory,
                    Collections.singletonList("-version"), cancellation, timeoutMillis,
                    (stream, line) -> output.add(line));
            checkCancelled(cancellation);
            if (!result.isSuccess()) {
                return Candidate.unavailable(id, executable.get(), expectedMajorFromId(id),
                        "Java -version exited with code " + result.getExitCode());
            }
            int actualMajor;
            try {
                actualMajor = JavaRuntimeProbe.parseMajorVersion(String.join("\n", output));
            } catch (IllegalArgumentException exception) {
                return Candidate.unavailable(id, executable.get(), expectedMajorFromId(id),
                        "Could not parse Java version output: " + safeMessage(exception));
            }
            return Candidate.available(id, executable.get(), actualMajor,
                    "Java " + actualMajor + " version probe passed");
        } catch (InterruptedIOException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            if (cancellation.isCancelled()) {
                InterruptedIOException cancelled = new InterruptedIOException("Java runtime discovery was cancelled");
                cancelled.initCause(exception);
                throw cancelled;
            }
            return Candidate.unavailable(id, null, expectedMajorFromId(id), safeMessage(exception));
        }
    }

    private static int expectedMajorFromId(String id) {
        if (id.startsWith("java-") || id.startsWith("jre-")) {
            String tail = id.substring(id.indexOf('-') + 1);
            int end = 0;
            while (end < tail.length() && Character.isDigit(tail.charAt(end))) end++;
            if (end > 0) {
                try {
                    int major = Integer.parseInt(tail.substring(0, end));
                    if (major >= 8 && major <= 99) return major;
                } catch (NumberFormatException ignored) {
                    // The ID is only a display hint; the real version comes from java -version.
                }
            }
        }
        return 0;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) return exception.getClass().getSimpleName();
        String sanitized = message.replace('\n', ' ').replace('\r', ' ').trim();
        return sanitized.length() <= 240 ? sanitized : sanitized.substring(0, 240);
    }

    private static void checkCancelled(CancellationToken cancellation) throws InterruptedIOException {
        if (cancellation.isCancelled()) throw new InterruptedIOException("Java runtime discovery was cancelled");
    }

    public static final class Candidate {
        public enum State { NOT_INSTALLED, AVAILABLE, UNAVAILABLE }

        private final String runtimeId;
        private final Path executable;
        private final int javaMajorVersion;
        private final int expectedMajorVersion;
        private final State state;
        private final String message;

        private Candidate(String runtimeId, Path executable, int javaMajorVersion, int expectedMajorVersion,
                          State state, String message) {
            this.runtimeId = runtimeId;
            this.executable = executable;
            this.javaMajorVersion = javaMajorVersion;
            this.expectedMajorVersion = expectedMajorVersion;
            this.state = state;
            this.message = message;
        }

        private static Candidate missing(String id, int expectedMajor) {
            return new Candidate(id, null, 0, expectedMajor, State.NOT_INSTALLED, "Runtime is not installed");
        }

        private static Candidate available(String id, Path executable, int actualMajor, String message) {
            return new Candidate(id, executable, actualMajor, actualMajor, State.AVAILABLE, message);
        }

        private static Candidate unavailable(String id, Path executable, int expectedMajor, String message) {
            return new Candidate(id, executable, 0, expectedMajor, State.UNAVAILABLE, message);
        }

        public String getRuntimeId() { return runtimeId; }
        public Path getExecutable() { return executable; }
        /** Returns zero if the runtime is missing or could not be probed. */
        public int getJavaMajorVersion() { return javaMajorVersion; }
        /** Returns zero when the ID does not imply a conventional Java major version. */
        public int getExpectedMajorVersion() { return expectedMajorVersion; }
        public State getState() { return state; }
        public String getMessage() { return message; }
        public boolean isAvailable() { return state == State.AVAILABLE; }
        public boolean isInstalled() { return state != State.NOT_INSTALLED; }

        public String getDisplayLabel() {
            if (state == State.AVAILABLE) return "Java " + javaMajorVersion + " · " + runtimeId + " · probe passed";
            if (state == State.NOT_INSTALLED) {
                String javaLabel = expectedMajorVersion == 0 ? "Java version" : "Java " + expectedMajorVersion;
                return javaLabel + " · " + runtimeId + " · not installed";
            }
            String javaLabel = expectedMajorVersion == 0 ? "Runtime" : "Java " + expectedMajorVersion;
            return javaLabel + " · " + runtimeId + " · unavailable";
        }
    }
}
