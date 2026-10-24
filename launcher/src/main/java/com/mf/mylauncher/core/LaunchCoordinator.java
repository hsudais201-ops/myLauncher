package com.mf.mylauncher.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates final launch checks, argument expansion and child-process execution.
 * It does not provide a Java runtime, Android renderer/GL bridge, or account authentication.
 */
public final class LaunchCoordinator {
    private final LaunchPreflight preflight;
    private final JavaProcessRunner processRunner;

    public LaunchCoordinator() {
        this(new LaunchPreflight(), new JavaProcessRunner());
    }

    LaunchCoordinator(LaunchPreflight preflight, JavaProcessRunner processRunner) {
        if (preflight == null || processRunner == null) {
            throw new IllegalArgumentException("Launch preflight and process runner are required");
        }
        this.preflight = preflight;
        this.processRunner = processRunner;
    }

    /**
     * Starts the configured Java main class after validating the install layout, index, classpath,
     * executable version and native directory. Argument values are passed directly to ProcessBuilder.
     */
    public LaunchResult launch(VersionDetails details, LaunchContext context, Path javaExecutable,
                               CancellationToken cancellation, long timeoutMillis,
                               JavaProcessRunner.OutputListener outputListener)
            throws IOException, InterruptedException {
        if (details == null) throw new IllegalArgumentException("Version details are required");
        if (context == null) throw new IllegalArgumentException("Launch context is required");
        if (javaExecutable == null) throw new IllegalArgumentException("Java executable is required");
        if (cancellation == null) throw new IllegalArgumentException("Cancellation token is required");
        checkCancelled(cancellation);

        Path gameRoot = context.getGameDirectoryPath().toAbsolutePath().normalize();
        requireDirectoryWithoutSymlinks(gameRoot, "Game directory");
        Path expectedAssets = gameRoot.resolve("assets");
        Path expectedLibraries = gameRoot.resolve("libraries");
        Path expectedNatives = gameRoot.resolve("natives").resolve(details.getId());
        requireExpectedDirectory(context.getAssetsRootPath(), expectedAssets, "assets root");
        requireExpectedDirectory(context.getLibrariesDirectoryPath(), expectedLibraries, "libraries directory");
        requireExpectedDirectory(context.getNativesDirectoryPath(), expectedNatives, "native-library directory");

        Path indexFile = expectedAssets.resolve("indexes").resolve(details.getAssetIndex().getId() + ".json");
        if (!VerifiedFileDownloader.isVerifiedFile(indexFile, details.getAssetIndex().getSizeBytes(),
                details.getAssetIndex().getSha1())) {
            throw new IOException("Installed asset index is missing or fails its size/SHA-1 check");
        }

        LaunchPreflight.Result inspected = preflight.inspect(details, context.getPlatform(),
                gameRoot, javaExecutable, cancellation, timeoutMillis);
        if (!inspected.getClasspath().getClasspath().equals(context.getClasspath())) {
            throw new IOException("Launch context classpath does not match the verified installed files");
        }
        String expectedLoggingPath = inspected.getClasspath().getLoggingConfigurationPath();
        if (!expectedLoggingPath.equals(context.getLoggingConfigPath())) {
            throw new IOException("Launch logging-configuration path does not match version metadata");
        }

        checkCancelled(cancellation);
        List<String> arguments = LaunchCommandBuilder.build(details, context);
        JavaProcessRunner.Result child = processRunner.run(javaExecutable, gameRoot, arguments,
                cancellation, timeoutMillis, outputListener);
        return new LaunchResult(inspected, child);
    }

    private static void requireExpectedDirectory(Path actual, Path expected, String label) throws IOException {
        if (actual == null || !actual.toAbsolutePath().normalize().equals(expected.toAbsolutePath().normalize())) {
            throw new IOException("Launch " + label + " must be located at " + expected);
        }
        requireDirectoryWithoutSymlinks(expected, "Launch " + label);
    }

    private static void requireDirectoryWithoutSymlinks(Path path, String label) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.getRoot() == null) throw new IOException(label + " must be an absolute path");
        Path current = normalized.getRoot();
        for (Path segment : normalized) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are not allowed in " + label.toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " must be an existing directory");
        }
    }

    private static void checkCancelled(CancellationToken cancellation) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || cancellation.isCancelled()) {
            throw new InterruptedIOException("Minecraft launch was cancelled");
        }
    }

    public static final class LaunchResult {
        private final LaunchPreflight.Result preflight;
        private final JavaProcessRunner.Result process;

        private LaunchResult(LaunchPreflight.Result preflight, JavaProcessRunner.Result process) {
            this.preflight = preflight;
            this.process = process;
        }

        public LaunchPreflight.Result getPreflight() { return preflight; }
        public JavaProcessRunner.Result getProcess() { return process; }
        public boolean isSuccess() { return process.isSuccess(); }
        public int getExitCode() { return process.getExitCode(); }
        public long getElapsedMillis() { return process.getElapsedMillis(); }
        public int getStdoutLineCount() { return process.getStdoutLineCount(); }
        public int getStderrLineCount() { return process.getStderrLineCount(); }
    }
}
