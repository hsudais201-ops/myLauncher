package com.mf.mylauncher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finds an already-installed Java runtime under an application-private runtime root.
 * Runtime installers/providers own provisioning; this class never downloads or executes unknown files.
 */
public final class JavaRuntimeLocator {
    private static final Pattern RUNTIME_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}");

    private final Path privateFilesRoot;

    public JavaRuntimeLocator(Path privateFilesRoot) {
        if (privateFilesRoot == null) throw new IllegalArgumentException("Private files root is required");
        this.privateFilesRoot = privateFilesRoot.toAbsolutePath().normalize();
    }

    /**
     * Looks for <privateFilesRoot>/runtimes/<runtimeId>/bin/java.
     * Missing installations return empty; unsafe or malformed installed paths fail closed.
     */
    public Optional<Path> locate(String runtimeId) throws IOException {
        if (runtimeId == null || !RUNTIME_ID.matcher(runtimeId).matches() || runtimeId.contains("..")) {
            throw new IllegalArgumentException("Runtime ID is invalid");
        }
        requireNonSymlinkPath(privateFilesRoot);
        if (!Files.isDirectory(privateFilesRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Private files root must be an existing directory");
        }

        Path runtimes = privateFilesRoot.resolve("runtimes");
        Path runtime = runtimes.resolve(runtimeId);
        Path bin = runtime.resolve("bin");
        Path executable = bin.resolve("java");
        if (!directoryOrMissing(runtimes, "Runtime directory")
                || !directoryOrMissing(runtime, "Selected runtime directory")
                || !directoryOrMissing(bin, "Runtime bin directory")) {
            return Optional.empty();
        }
        if (!Files.exists(executable, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        requireNonSymlinkPath(executable);
        if (!Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS) || !Files.isExecutable(executable)) {
            throw new IOException("Installed Java runtime must be a regular executable file");
        }
        Path resolved = executable.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!resolved.startsWith(privateFilesRoot)) {
            throw new IOException("Installed Java runtime escapes the private files root");
        }
        return Optional.of(resolved);
    }

    private static boolean directoryOrMissing(Path path, String label) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return false;
        requireNonSymlinkPath(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " must be a directory");
        }
        return true;
    }

    private static void requireNonSymlinkPath(Path path) throws IOException {
        Path current = path.getRoot();
        for (Path segment : path) {
            current = current == null ? segment : current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are not allowed in runtime paths");
            }
        }
    }
}
