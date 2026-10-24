package com.mf.mylauncher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the on-disk game classpath only after every required client/library file is revalidated.
 * It does not imply that the current Android device has a compatible Java runtime or native libraries.
 */
public final class ClasspathResolver {
    private ClasspathResolver() { }

    public static Result resolve(VersionDetails details, LibraryPlatform platform, Path gameDirectory)
            throws IOException {
        if (details == null) throw new IllegalArgumentException("Version details are required");
        if (platform == null) throw new IllegalArgumentException("Launch platform is required");
        if (gameDirectory == null) throw new IllegalArgumentException("Game directory is required");

        Path root = gameDirectory.toAbsolutePath().normalize();
        requireDirectory(root, "Game directory");

        Path client = safeResolve(root, "versions/" + details.getId() + "/" + details.getId() + ".jar");
        verify(client, details.getClientDownload(), "Minecraft client JAR");
        Set<Path> classpath = new LinkedHashSet<>();
        for (LibraryMetadata library : details.getLibraries()) {
            if (!library.isAllowedOn(platform.getOsName(), platform.getArchitecture(),
                    platform.getOsVersion(), platform.getFeatures())) continue;
            if (!library.isCompatibleWithPlatform(platform)) continue;
            if (library.hasExplicitNativeClassifier()) continue;
            Optional<DownloadArtifact> optionalArtifact = library.getArtifact();
            if (!optionalArtifact.isPresent()) {
                // Native-only classifiers are extracted/resolved separately; they are not classpath JARs.
                continue;
            }
            DownloadArtifact artifact = optionalArtifact.get();
            if (!artifact.hasPath()) {
                throw new IOException("Library artifact path is missing for " + library.getName());
            }
            Path libraryFile = safeResolve(root, "libraries/" + artifact.getPath());
            verify(libraryFile, artifact, "Minecraft library " + library.getName());
            classpath.add(libraryFile);
        }
        classpath.add(client);

        String loggingPath = "";
        if (details.getLoggingConfiguration().isPresent()) {
            LoggingConfigurationMetadata logging = details.getLoggingConfiguration().get();
            Path config = safeResolve(root, "assets/log_configs/" + logging.getId());
            DownloadArtifact artifact = new DownloadArtifact(logging.getUrl(), logging.getSha1(),
                    logging.getSizeBytes(), null);
            verify(config, artifact, "Minecraft logging configuration");
            loggingPath = config.toString();
        }

        List<Path> entries = Collections.unmodifiableList(new ArrayList<>(classpath));
        StringBuilder joined = new StringBuilder();
        for (Path entry : entries) {
            if (joined.length() > 0) joined.append(java.io.File.pathSeparatorChar);
            joined.append(entry);
        }
        return new Result(root, entries, joined.toString(), loggingPath);
    }

    private static Path safeResolve(Path root, String relative) throws IOException {
        if (relative == null || relative.isEmpty() || relative.startsWith("/")
                || relative.startsWith("\\") || relative.contains("\\") || relative.contains(":")) {
            throw new IOException("Launch file relative path is unsafe");
        }
        for (String segment : relative.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IOException("Launch file path contains traversal components");
            }
        }
        Path resolved = root.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IOException("Launch file path escapes the game directory");
        }
        Path current = root;
        rejectSymlink(current);
        for (Path segment : root.relativize(resolved)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) rejectSymlink(current);
        }
        return resolved;
    }

    private static void requireDirectory(Path path, String label) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        if (current == null) throw new IOException(label + " must be an absolute path");
        for (Path segment : absolute) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IOException(label + " path must not contain symbolic links");
            }
        }
        if (!Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " must be an existing non-symlink directory");
        }
    }

    private static void verify(Path file, DownloadArtifact artifact, String label) throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " is missing or is not a regular file: " + file);
        }
        if (Files.size(file) != artifact.getSizeBytes()) {
            throw new IOException(label + " size does not match metadata: " + file);
        }
        MessageDigest digest = sha1Digest();
        try (java.io.InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > 0) digest.update(buffer, 0, count);
            }
        }
        String actual = toHex(digest.digest());
        if (!actual.equalsIgnoreCase(artifact.getSha1())) {
            throw new IOException(label + " SHA-1 does not match metadata: " + file);
        }
    }

    private static void rejectSymlink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Symbolic links are not allowed in launch file paths");
    }

    private static MessageDigest sha1Digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is required by the Java runtime", exception);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    public static final class Result {
        private final Path gameDirectory;
        private final List<Path> classpathEntries;
        private final String classpath;
        private final String loggingConfigurationPath;

        private Result(Path gameDirectory, List<Path> classpathEntries, String classpath,
                       String loggingConfigurationPath) {
            this.gameDirectory = gameDirectory;
            this.classpathEntries = classpathEntries;
            this.classpath = classpath;
            this.loggingConfigurationPath = loggingConfigurationPath;
        }

        public Path getGameDirectory() { return gameDirectory; }
        public List<Path> getClasspathEntries() { return classpathEntries; }
        public String getClasspath() { return classpath; }
        public String getLoggingConfigurationPath() { return loggingConfigurationPath; }
    }
}
