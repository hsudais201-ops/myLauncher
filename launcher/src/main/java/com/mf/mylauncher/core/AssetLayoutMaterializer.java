package com.mf.mylauncher.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Materializes legacy virtual assets and resources/ layouts from verified asset-object cache files. */
public final class AssetLayoutMaterializer {
    private static final Pattern SAFE_INDEX_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}");
    private static final int BUFFER_SIZE = 32 * 1024;

    private AssetLayoutMaterializer() { }

    public static Result materialize(AssetIndex index, String assetIndexId,
                                     Path assetsRoot, Path gameRoot,
                                     CancellationToken cancellation) throws IOException {
        if (index == null) throw new IllegalArgumentException("Asset index is required");
        if (assetIndexId == null || !SAFE_INDEX_ID.matcher(assetIndexId).matches() || assetIndexId.contains("..")) {
            throw new IllegalArgumentException("Asset-index ID is unsafe");
        }
        if (assetsRoot == null || gameRoot == null) throw new IllegalArgumentException("Asset/game root paths are required");
        if (cancellation == null) throw new IllegalArgumentException("Cancellation token is required");

        Path normalizedAssets = assetsRoot.toAbsolutePath().normalize();
        Path normalizedGame = gameRoot.toAbsolutePath().normalize();
        prepareDirectoriesNoSymlinks(normalizedAssets);
        prepareDirectoriesNoSymlinks(normalizedGame);

        int written = 0;
        int cached = 0;
        long bytesWritten = 0;
        if (!index.isVirtual() && !index.isMapToResources()) {
            return new Result(written, cached, bytesWritten);
        }

        for (AssetObjectMetadata asset : index.getOrderedObjects()) {
            checkCancelled(cancellation);
            Path object = safeResolve(normalizedAssets, asset.getObjectRelativePath());
            if (!Files.isRegularFile(object, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(object)) {
                throw new IOException("Verified asset object is missing: " + asset.getObjectRelativePath());
            }
            if (!hasExpectedContent(object, asset, cancellation)) {
                throw new IOException("Cached asset object failed integrity verification: " + asset.getObjectRelativePath());
            }

            if (index.isVirtual()) {
                Path destination = safeResolve(normalizedAssets,
                        "virtual/" + assetIndexId + "/" + asset.getResourceRelativePath());
                boolean reused = copyVerifiedIfNeeded(object, destination, asset, cancellation);
                if (reused) cached++;
                else {
                    written++;
                    bytesWritten = Math.addExact(bytesWritten, asset.getSizeBytes());
                }
            }
            if (index.isMapToResources()) {
                Path destination = safeResolve(normalizedGame, "resources/" + asset.getResourceRelativePath());
                boolean reused = copyVerifiedIfNeeded(object, destination, asset, cancellation);
                if (reused) cached++;
                else {
                    written++;
                    bytesWritten = Math.addExact(bytesWritten, asset.getSizeBytes());
                }
            }
        }
        return new Result(written, cached, bytesWritten);
    }

    private static boolean copyVerifiedIfNeeded(Path source, Path destination,
                                                 AssetObjectMetadata metadata, CancellationToken cancellation)
            throws IOException {
        Path parent = destination.getParent();
        if (parent == null) throw new IOException("Asset materialization destination has no parent");
        prepareDirectoriesNoSymlinks(parent);
        rejectSymlink(destination);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Asset materialization destination is not a regular file");
            }
            if (hasExpectedContent(destination, metadata, cancellation)) return true;
        }

        Path temporary = Files.createTempFile(parent, ".mylauncher-asset-", ".part");
        boolean published = false;
        try {
            MessageDigest digest = sha1Digest();
            long total = 0;
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream input = Files.newInputStream(source); OutputStream output = Files.newOutputStream(temporary)) {
                int count;
                while (true) {
                    checkCancelled(cancellation);
                    count = input.read(buffer);
                    if (count == -1) break;
                    if (count == 0) continue;
                    total += count;
                    if (total > metadata.getSizeBytes()) {
                        throw new IOException("Asset object grew beyond its declared size");
                    }
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                    checkCancelled(cancellation);
                }
            }
            if (total != metadata.getSizeBytes()
                    || !toHex(digest.digest()).equalsIgnoreCase(metadata.getHash())) {
                throw new IOException("Materialized asset failed size or SHA-1 verification");
            }
            rejectSymlink(destination);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            published = true;
            return false;
        } finally {
            if (!published) Files.deleteIfExists(temporary);
        }
    }

    private static Path safeResolve(Path root, String relativePath) throws IOException {
        if (relativePath == null || relativePath.isEmpty() || relativePath.startsWith("/")
                || relativePath.startsWith("\\") || relativePath.contains("\\") || relativePath.contains(":")) {
            throw new IOException("Asset relative path is unsafe");
        }
        for (String segment : relativePath.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IOException("Asset relative path contains traversal components");
            }
        }
        Path resolved = root.resolve(relativePath.replace('/', java.io.File.separatorChar)).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IOException("Asset path escapes its designated root");
        }
        return resolved;
    }

    private static void prepareDirectoriesNoSymlinks(Path directory) throws IOException {
        Path absolute = directory.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        if (current == null) throw new IOException("Asset path must be absolute");
        for (Path segment : absolute) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                rejectSymlink(current);
                if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Asset path component is not a directory");
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    private static boolean hasExpectedContent(Path path, AssetObjectMetadata metadata,
                                               CancellationToken cancellation) throws IOException {
        if (Files.size(path) != metadata.getSizeBytes()) return false;
        MessageDigest digest = sha1Digest();
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = Files.newInputStream(path)) {
            int count;
            while (true) {
                checkCancelled(cancellation);
                count = input.read(buffer);
                if (count == -1) break;
                if (count == 0) continue;
                total += count;
                if (total > metadata.getSizeBytes()) return false;
                digest.update(buffer, 0, count);
            }
        }
        return total == metadata.getSizeBytes()
                && toHex(digest.digest()).equalsIgnoreCase(metadata.getHash());
    }

    private static void checkCancelled(CancellationToken cancellation) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || cancellation.isCancelled()) {
            throw new InterruptedIOException("Asset materialization was cancelled");
        }
    }

    private static void rejectSymlink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Symbolic links are not allowed in asset paths");
    }

    private static MessageDigest sha1Digest() {
        try { return MessageDigest.getInstance("SHA-1"); }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is required by the Java runtime", exception);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    public static final class Result {
        private final int filesWritten;
        private final int filesReused;
        private final long bytesWritten;

        private Result(int filesWritten, int filesReused, long bytesWritten) {
            this.filesWritten = filesWritten;
            this.filesReused = filesReused;
            this.bytesWritten = bytesWritten;
        }
        public int getFilesWritten() { return filesWritten; }
        public int getFilesReused() { return filesReused; }
        public long getBytesWritten() { return bytesWritten; }
    }
}
