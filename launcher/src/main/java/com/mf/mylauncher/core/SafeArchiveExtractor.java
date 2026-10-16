package com.mf.mylauncher.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** ZIP extraction for native-library archives with traversal, symlink, duplicate and bomb limits. */
public final class SafeArchiveExtractor {
    public static final int MAX_ENTRIES = 20_000;
    public static final long MAX_ENTRY_BYTES = 128L * 1024L * 1024L;
    public static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 512L * 1024L * 1024L;
    private static final int BUFFER_SIZE = 16 * 1024;

    private SafeArchiveExtractor() { }

    /**
     * Extracts into a staging directory and publishes it only after the entire archive passes validation.
     * Directory excludes such as "META-INF/" exclude the directory and all descendants; other excludes
     * match one exact relative entry path.
     */
    public static ExtractionResult extract(Path archive, Path destination, List<String> excludes) throws IOException {
        return extractAll(Collections.singletonList(new ArchiveSpec(archive, excludes)), destination, CancellationToken.NONE);
    }

    public static ExtractionResult extract(Path archive, Path destination, List<String> excludes,
                                           CancellationToken cancellation) throws IOException {
        return extractAll(Collections.singletonList(new ArchiveSpec(archive, excludes)), destination, cancellation);
    }

    /**
     * Extracts multiple archives into one staging directory, applying each archive's own exclusions.
     * Duplicate unexcluded file names across archives are rejected rather than silently overwritten.
     */
    public static ExtractionResult extractAll(List<ArchiveSpec> archives, Path destination) throws IOException {
        return extractAll(archives, destination, CancellationToken.NONE);
    }

    /** Cancellation aborts before publishing staging, preserving the previous destination. */
    public static ExtractionResult extractAll(List<ArchiveSpec> archives, Path destination,
                                              CancellationToken cancellation) throws IOException {
        if (cancellation == null) throw new IllegalArgumentException("Cancellation token is required");
        checkCancelled(cancellation);
        if (archives == null || archives.isEmpty()) {
            throw new IllegalArgumentException("At least one native archive is required");
        }
        if (destination == null) throw new IllegalArgumentException("Extraction destination is required");
        List<ArchiveSpec> validatedArchives = new ArrayList<>();
        for (ArchiveSpec spec : archives) {
            if (spec == null) throw new IllegalArgumentException("Archive specifications must not contain null entries");
            Path archive = spec.archive;
            if (archive == null || Files.isSymbolicLink(archive)
                    || !Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Native archive must be an existing regular non-symlink file");
            }
            validatedArchives.add(new ArchiveSpec(archive,
                    validateExcludes(spec.excludes)));
        }

        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || target.equals(target.getRoot())) {
            throw new IOException("Extraction destination must be a non-root directory");
        }
        prepareDirectories(parent);
        rejectSymlink(target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Extraction destination exists and is not a directory");
        }

        Path staging = Files.createTempDirectory(parent, ".mylauncher-extract-" + UUID.randomUUID() + "-");
        boolean published = false;
        Path backup = null;
        try {
            MutableStats stats = new MutableStats();
            Set<String> seenFiles = new HashSet<>();
            for (ArchiveSpec spec : validatedArchives) {
                checkCancelled(cancellation);
                extractInto(spec.archive, staging, spec.excludes, stats, seenFiles, cancellation);
            }

            rejectSymlink(target);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                backup = parent.resolve(".mylauncher-extract-backup-" + UUID.randomUUID());
                moveDirectory(target, backup);
            }
            try {
                moveDirectory(staging, target);
                published = true;
            } catch (IOException exception) {
                if (backup != null && Files.exists(backup, LinkOption.NOFOLLOW_LINKS)
                        && !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    moveDirectory(backup, target);
                    backup = null;
                }
                throw exception;
            }
            if (backup != null) {
                deleteTree(backup);
                backup = null;
            }
            return new ExtractionResult(target, stats.fileCount, stats.directoryCount,
                    stats.bytesWritten, stats.bytesRead, stats.entryCount);
        } finally {
            if (!published && Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) deleteTree(staging);
            if (backup != null && Files.exists(backup, LinkOption.NOFOLLOW_LINKS)
                    && !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                moveDirectory(backup, target);
            }
        }
    }

    private static void extractInto(Path archive, Path staging, List<String> excludes,
                                    MutableStats stats, Set<String> seenFiles,
                                    CancellationToken cancellation) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream fileInput = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(fileInput)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                checkCancelled(cancellation);
                stats.entryCount++;
                if (stats.entryCount > MAX_ENTRIES) throw new IOException("Native archive collection contains too many ZIP entries");

                String rawName = entry.getName();
                boolean directory = entry.isDirectory() || (rawName != null && rawName.endsWith("/"));
                String relative = validateEntryName(rawName, directory);
                boolean excluded = isExcluded(relative, excludes);
                if (!directory && !excluded && !seenFiles.add(relative)) {
                    throw new IOException("Native archive collection contains a duplicate file entry: " + relative);
                }
                if (entry.getSize() > MAX_ENTRY_BYTES) {
                    throw new IOException("Native archive entry exceeds the uncompressed size limit: " + relative);
                }
                Path output = null;
                if (!excluded) {
                    output = staging.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
                    if (!output.startsWith(staging) || output.equals(staging)) {
                        throw new IOException("ZIP entry escapes the staging directory");
                    }
                    if (directory) {
                        prepareDirectories(output);
                        stats.directoryCount++;
                    } else {
                        prepareDirectories(output.getParent());
                    }
                }

                OutputStream fileOutput = null;
                try {
                    if (!excluded && !directory) fileOutput = Files.newOutputStream(output);
                    long entryBytes = 0;
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        checkCancelled(cancellation);
                        if (count == 0) continue;
                        entryBytes += count;
                        stats.bytesRead += count;
                        if (entryBytes > MAX_ENTRY_BYTES) {
                            throw new IOException("Native archive entry exceeds the uncompressed size limit: " + relative);
                        }
                        if (stats.bytesRead > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                            throw new IOException("Native archive collection exceeds the total uncompressed size limit");
                        }
                        if (fileOutput != null) {
                            fileOutput.write(buffer, 0, count);
                            stats.bytesWritten += count;
                        }
                    }
                    if (!excluded && !directory) stats.fileCount++;
                } finally {
                    if (fileOutput != null) fileOutput.close();
                    zip.closeEntry();
                }
            }
        }
    }

    private static void checkCancelled(CancellationToken cancellation) throws java.io.InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || cancellation.isCancelled()) {
            throw new java.io.InterruptedIOException("Native archive extraction was cancelled");
        }
    }

    private static String validateEntryName(String value, boolean directory) throws IOException {
        if (value == null || value.isEmpty() || value.startsWith("/") || value.startsWith("\\")
                || value.contains("\\") || value.contains(":") || value.indexOf('\0') >= 0) {
            throw new IOException("Native archive contains an unsafe entry path");
        }
        String name = directory && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        if (name.isEmpty()) throw new IOException("Native archive contains an empty entry path");
        for (String segment : name.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IOException("Native archive entry contains path traversal: " + value);
            }
            for (int i = 0; i < segment.length(); i++) {
                char ch = segment.charAt(i);
                if (ch < 0x20 || ch == 0x7f) throw new IOException("Native archive entry contains control characters");
            }
        }
        return name;
    }

    private static List<String> validateExcludes(List<String> excludes) {
        if (excludes == null || excludes.isEmpty()) return Collections.emptyList();
        List<String> safe = new ArrayList<>();
        for (String exclude : excludes) {
            if (exclude == null || exclude.isEmpty()) {
                throw new IllegalArgumentException("Native archive exclusions must not be empty");
            }
            String path = exclude.endsWith("/") ? exclude.substring(0, exclude.length() - 1) : exclude;
            try {
                validateEntryName(path, false);
            } catch (IOException exception) {
                throw new IllegalArgumentException("Unsafe native archive exclusion path", exception);
            }
            safe.add(exclude);
        }
        return Collections.unmodifiableList(safe);
    }

    private static boolean isExcluded(String entry, List<String> excludes) {
        for (String rule : excludes) {
            if (rule.endsWith("/")) {
                String directory = rule.substring(0, rule.length() - 1);
                if (entry.equals(directory) || entry.startsWith(directory + "/")) return true;
            } else if (entry.equals(rule)) {
                return true;
            }
        }
        return false;
    }

    private static void prepareDirectories(Path directory) throws IOException {
        if (directory == null) throw new IOException("Archive path has no parent directory");
        Path absolute = directory.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        if (current == null) throw new IOException("Archive path must be absolute");
        for (Path segment : absolute) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                rejectSymlink(current);
                if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Archive path component is not a directory");
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    private static void rejectSymlink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Symbolic links are not allowed in extraction paths");
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            Path[] all = paths.sorted(java.util.Comparator.reverseOrder()).toArray(Path[]::new);
            for (Path path : all) Files.deleteIfExists(path);
        }
    }

    public static final class ArchiveSpec {
        private final Path archive;
        private final List<String> excludes;

        public ArchiveSpec(Path archive, List<String> excludes) {
            this.archive = archive;
            this.excludes = excludes == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(excludes));
        }

        public Path getArchive() { return archive; }
        public List<String> getExcludes() { return excludes; }
    }

    private static final class MutableStats {
        int fileCount;
        int directoryCount;
        int entryCount;
        long bytesWritten;
        long bytesRead;
    }

    public static final class ExtractionResult {
        private final Path destination;
        private final int fileCount, directoryCount, entryCount;
        private final long bytesWritten, bytesRead;

        private ExtractionResult(Path destination, int fileCount, int directoryCount,
                                 long bytesWritten, long bytesRead, int entryCount) {
            this.destination = destination;
            this.fileCount = fileCount;
            this.directoryCount = directoryCount;
            this.bytesWritten = bytesWritten;
            this.bytesRead = bytesRead;
            this.entryCount = entryCount;
        }
        public Path getDestination() { return destination; }
        public int getFileCount() { return fileCount; }
        public int getDirectoryCount() { return directoryCount; }
        public int getEntryCount() { return entryCount; }
        public long getBytesWritten() { return bytesWritten; }
        public long getBytesRead() { return bytesRead; }
    }
}
