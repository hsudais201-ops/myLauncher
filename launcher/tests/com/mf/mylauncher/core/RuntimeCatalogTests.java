package com.mf.mylauncher.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Comparator;
import java.util.stream.Stream;

/** Regression tests for safe multi-runtime discovery and exact Java-major selection. */
public final class RuntimeCatalogTests {
    private static int checks;
    private RuntimeCatalogTests() { }

    public static void main(String[] args) throws Exception {
        discoversAndSelectsByActualVersion();
        reportsBrokenRuntimeWithoutSelectingIt();
        rejectsCancellation();
        System.out.println("PASS: " + checks + " Java-runtime-catalog assertions");
    }

    private static void discoversAndSelectsByActualVersion() throws Exception {
        Path root = Files.createTempDirectory("runtime-catalog");
        try {
            Path java17 = installFakeRuntime(root, "java-17", "17.0.10");
            installFakeRuntime(root, "experimental-jre", "21.0.5");
            List<JavaRuntimeCatalog.Candidate> catalog = JavaRuntimeCatalog.discover(
                    root, root, CancellationToken.NONE, 2_000);
            JavaRuntimeCatalog.Candidate seventeen = byId(catalog, "java-17");
            yes(seventeen.isAvailable(), "Java 17 runtime is probed");
            eq(17, seventeen.getJavaMajorVersion(), "actual Java 17 major parsed");
            eq(java17.resolve("bin").resolve("java").toRealPath(),
                    seventeen.getExecutable(), "executable path points to the probed runtime");
            eq(JavaRuntimeCatalog.Candidate.State.NOT_INSTALLED, byId(catalog, "java-8").getState(),
                    "missing conventional Java 8 slot is visible");
            eq(JavaRuntimeCatalog.Candidate.State.NOT_INSTALLED, byId(catalog, "java-25").getState(),
                    "missing conventional Java 25 slot is visible");
            eq(21, byId(catalog, "experimental-jre").getJavaMajorVersion(),
                    "custom runtime ID uses actual version output, not its name");
            Optional<JavaRuntimeCatalog.Candidate> selected17 = JavaRuntimeCatalog.selectFor(catalog, 17);
            yes(selected17.isPresent(), "Java 17 exact match can be selected");
            eq("java-17", selected17.get().getRuntimeId(), "canonical Java runtime is preferred");
            Optional<JavaRuntimeCatalog.Candidate> selected21 = JavaRuntimeCatalog.selectFor(catalog, 21);
            yes(selected21.isPresent(), "custom Java 21 exact match can be selected");
            eq("experimental-jre", selected21.get().getRuntimeId(), "custom runtime is available by actual major");
            yes(!JavaRuntimeCatalog.selectFor(catalog, 25).isPresent(),
                    "missing Java major is not silently replaced with another runtime");
            boolean immutable = false;
            try {
                catalog.clear();
            } catch (UnsupportedOperationException expected) {
                immutable = true;
            }
            yes(immutable, "catalog result is immutable");
        } finally {
            deleteTree(root);
        }
    }

    private static void reportsBrokenRuntimeWithoutSelectingIt() throws Exception {
        Path root = Files.createTempDirectory("runtime-catalog-broken");
        try {
            Path java = root.resolve("runtimes").resolve("java-21").resolve("bin").resolve("java");
            Files.createDirectories(java.getParent());
            Files.write(java, "#!/bin/sh\necho not-a-java-runtime >&2\nexit 0\n".getBytes(StandardCharsets.UTF_8));
            yes(java.toFile().setExecutable(true), "fake executable bit can be set for the test");
            Path noexec = root.resolve("runtimes").resolve("broken-jre").resolve("bin").resolve("java");
            Files.createDirectories(noexec.getParent());
            Files.write(noexec, "not executable".getBytes(StandardCharsets.UTF_8));

            List<JavaRuntimeCatalog.Candidate> catalog = JavaRuntimeCatalog.discover(
                    root, root, CancellationToken.NONE, 2_000);
            JavaRuntimeCatalog.Candidate invalidOutput = byId(catalog, "java-21");
            eq(JavaRuntimeCatalog.Candidate.State.UNAVAILABLE, invalidOutput.getState(),
                    "invalid java -version output is unavailable");
            JavaRuntimeCatalog.Candidate noExecutable = byId(catalog, "broken-jre");
            eq(JavaRuntimeCatalog.Candidate.State.UNAVAILABLE, noExecutable.getState(),
                    "non-executable candidate is unavailable");
            yes(!JavaRuntimeCatalog.selectFor(catalog, 21).isPresent(),
                    "unavailable runtime is never selected");
        } finally {
            deleteTree(root);
        }
    }

    private static void rejectsCancellation() throws Exception {
        Path root = Files.createTempDirectory("runtime-catalog-cancelled");
        try {
            boolean cancelled = false;
            try {
                JavaRuntimeCatalog.discover(root, root, () -> true, 1_000);
            } catch (InterruptedIOException expected) {
                cancelled = true;
            }
            yes(cancelled, "already-cancelled discovery stops before probing");
        } finally {
            deleteTree(root);
        }
    }

    private static Path installFakeRuntime(Path root, String id, String version) throws IOException {
        Path executable = root.resolve("runtimes").resolve(id).resolve("bin").resolve("java");
        Files.createDirectories(executable.getParent());
        String script = "#!/bin/sh\necho 'openjdk version \"" + version + "\"' >&2\nexit 0\n";
        Files.write(executable, script.getBytes(StandardCharsets.UTF_8));
        if (!executable.toFile().setExecutable(true)) {
            throw new IOException("Unable to make fake test runtime executable");
        }
        return executable.getParent().getParent();
    }

    private static JavaRuntimeCatalog.Candidate byId(List<JavaRuntimeCatalog.Candidate> catalog, String id) {
        return catalog.stream().filter(candidate -> candidate.getRuntimeId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("Expected runtime candidate missing: " + id));
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
            });
        }
    }

    private static void yes(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void eq(Object expected, Object actual, String message) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }
}
