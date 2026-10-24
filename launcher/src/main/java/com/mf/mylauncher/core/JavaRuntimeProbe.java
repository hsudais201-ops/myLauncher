package com.mf.mylauncher.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Verifies that a candidate Java executable actually starts and reports the required major version. */
public final class JavaRuntimeProbe {
    private static final Pattern QUOTED_VERSION = Pattern.compile(
            "(?i)(?:openjdk|java|openj9)\\s+version\\s+\"([^\"]+)\"");
    private static final Pattern BARE_VERSION = Pattern.compile(
            "(?im)^\\s*(?:openjdk|java)\\s+((?:1\\.)?\\d+(?:[._+\\-][A-Za-z0-9]+)*)");

    private final JavaProcessRunner processRunner;

    public JavaRuntimeProbe() {
        this(new JavaProcessRunner());
    }

    JavaRuntimeProbe(JavaProcessRunner processRunner) {
        if (processRunner == null) throw new IllegalArgumentException("Java process runner is required");
        this.processRunner = processRunner;
    }

    public RuntimeInfo inspect(Path javaExecutable, Path workingDirectory, int requiredMajorVersion,
                               CancellationToken cancellation, long timeoutMillis)
            throws IOException, InterruptedException {
        if (requiredMajorVersion < 8 || requiredMajorVersion > 99) {
            throw new IllegalArgumentException("Required Java major version is outside the supported range");
        }
        List<String> output = Collections.synchronizedList(new ArrayList<>());
        JavaProcessRunner.Result result = processRunner.run(javaExecutable, workingDirectory,
                Collections.singletonList("-version"), cancellation, timeoutMillis,
                (stream, line) -> output.add(line));
        String text = String.join("\n", output);
        if (!result.isSuccess()) {
            throw new IOException("Java runtime version check exited with code " + result.getExitCode());
        }
        int actualMajor = parseMajorVersion(text);
        if (actualMajor != requiredMajorVersion) {
            throw new IOException("Minecraft version requires Java " + requiredMajorVersion
                    + " but the selected runtime reports Java " + actualMajor);
        }
        Path resolved;
        try {
            resolved = javaExecutable.toAbsolutePath().normalize().toRealPath();
        } catch (IOException exception) {
            throw new IOException("Java executable disappeared during version check", exception);
        }
        return new RuntimeInfo(resolved, actualMajor, text, result.getElapsedMillis());
    }

    /** Handles legacy 1.8.0 version strings and modern 9+ major versions. */
    public static int parseMajorVersion(String output) {
        if (output == null || output.trim().isEmpty()) {
            throw new IllegalArgumentException("Java version output is empty");
        }
        Matcher quoted = QUOTED_VERSION.matcher(output);
        String version = quoted.find() ? quoted.group(1) : null;
        if (version == null) {
            Matcher bare = BARE_VERSION.matcher(output);
            if (bare.find()) version = bare.group(1);
        }
        if (version == null) throw new IllegalArgumentException("Java version output did not contain a recognizable version");
        String candidate = version.startsWith("1.") ? version.substring(2) : version;
        int index = 0;
        while (index < candidate.length() && Character.isDigit(candidate.charAt(index))) index++;
        if (index == 0) throw new IllegalArgumentException("Java version string did not start with a major version number");
        try {
            int major = Integer.parseInt(candidate.substring(0, index));
            if (major < 1 || major > 99) throw new NumberFormatException("major out of supported range");
            return major;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Java major version is invalid", exception);
        }
    }

    public static final class RuntimeInfo {
        private final Path executable;
        private final int majorVersion;
        private final String versionOutput;
        private final long probeDurationMillis;

        private RuntimeInfo(Path executable, int majorVersion, String versionOutput, long probeDurationMillis) {
            this.executable = executable;
            this.majorVersion = majorVersion;
            this.versionOutput = versionOutput;
            this.probeDurationMillis = probeDurationMillis;
        }

        public Path getExecutable() { return executable; }
        public int getMajorVersion() { return majorVersion; }
        public String getVersionOutput() { return versionOutput; }
        public long getProbeDurationMillis() { return probeDurationMillis; }
    }
}
