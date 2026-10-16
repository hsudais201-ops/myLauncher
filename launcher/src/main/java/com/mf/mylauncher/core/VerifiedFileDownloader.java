package com.mf.mylauncher.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;

/** Downloads a file to a safe cache path and only publishes it after size and SHA-1 verification. */
public final class VerifiedFileDownloader {
    public static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    public static final int READ_TIMEOUT_MILLIS = 30_000;
    private static final int BUFFER_SIZE = 32 * 1024;
    private static final Set<String> ALLOWED_HOSTS = new HashSet<>(Arrays.asList(
            "resources.download.minecraft.net",
            "piston-data.mojang.com",
            "launcher.mojang.com",
            "libraries.minecraft.net",
            "repo1.maven.org",
            "repo.maven.apache.org"));

    private final ConnectionOpener opener;

    public VerifiedFileDownloader() {
        this(JdkFileConnection::new);
    }

    VerifiedFileDownloader(ConnectionOpener opener) {
        if (opener == null) throw new IllegalArgumentException("Connection opener is required");
        this.opener = opener;
    }

    /** Downloads an artifact to the caller-selected path beneath cacheRoot. */
    public DownloadResult download(DownloadArtifact artifact, Path cacheRoot, String relativePath) throws IOException {
        if (artifact == null) throw new IllegalArgumentException("Artifact metadata is required");
        return download(artifact.getUrl(), cacheRoot, relativePath, artifact.getSizeBytes(), artifact.getSha1());
    }

    /** Downloads a Minecraft asset to its content-addressed object path beneath the asset cache. */
    public DownloadResult downloadAsset(AssetObjectMetadata asset, Path assetsRoot) throws IOException {
        if (asset == null) throw new IllegalArgumentException("Asset metadata is required");
        return download(asset.getDownloadUrl(), assetsRoot, asset.getObjectRelativePath(),
                asset.getSizeBytes(), asset.getHash());
    }

    /** Download a trusted artifact into a checked relative cache path. Verified cache hits are reused. */
    public DownloadResult download(URI source, Path cacheRoot, String relativePath,
                                   long expectedSizeBytes, String expectedSha1) throws IOException {
        return download(source, cacheRoot, relativePath, expectedSizeBytes, expectedSha1,
                CancellationToken.NONE, transferred -> { });
    }

    /** Variant supporting cooperative cancellation and per-file byte progress. */
    public DownloadResult download(URI source, Path cacheRoot, String relativePath,
                                   long expectedSizeBytes, String expectedSha1,
                                   CancellationToken cancellation, TransferProgressListener progress)
            throws IOException {
        if (cancellation == null) throw new IllegalArgumentException("Cancellation token is required");
        if (progress == null) throw new IllegalArgumentException("Transfer progress listener is required");
        checkCancelled(cancellation);
        validateSource(source);
        validateRelativePath(relativePath);
        validateExpectedMetadata(expectedSizeBytes, expectedSha1);
        if (cacheRoot == null) throw new IllegalArgumentException("Cache root is required");

        Path root = cacheRoot.toAbsolutePath().normalize();
        prepareRoot(root);
        Path destination = root.resolve(relativePath.replace('/', java.io.File.separatorChar)).normalize();
        if (!destination.startsWith(root) || destination.equals(root)) {
            throw new IOException("Resolved cache path escapes the cache root");
        }
        prepareParentDirectories(root, destination.getParent());
        rejectSymlink(destination);

        checkCancelled(cancellation);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Cache destination exists and is not a regular file");
            }
            if (isVerified(destination, expectedSizeBytes, expectedSha1)) {
                return new DownloadResult(destination, expectedSizeBytes, true);
            }
        }

        FileConnection connection = opener.open(source);
        if (connection == null) throw new IOException("Download connection could not be opened");
        Path temporary = null;
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/octet-stream");
            connection.setRequestProperty("User-Agent", "myLauncher/0.1 (Android; verified game download)");

            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Download failed with HTTP " + status);
            long declaredLength = connection.getContentLengthLong();
            if (declaredLength >= 0 && declaredLength != expectedSizeBytes) {
                throw new IOException("Download Content-Length did not match the expected size");
            }

            temporary = Files.createTempFile(destination.getParent(), ".mylauncher-download-", ".part");
            MessageDigest digest = sha1Digest();
            long total = 0;
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(temporary)) {
                int count;
                while (true) {
                    checkCancelled(cancellation);
                    count = input.read(buffer);
                    if (count == -1) break;
                    if (count == 0) continue;
                    if (total > expectedSizeBytes - count) {
                        throw new IOException("Downloaded file exceeds the expected size");
                    }
                    total += count;
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                    progress.onBytesTransferred(total);
                    checkCancelled(cancellation);
                }
            }
            checkCancelled(cancellation);
            if (total != expectedSizeBytes) throw new IOException("Downloaded file size did not match metadata");
            String actualSha1 = toHex(digest.digest());
            if (!actualSha1.equalsIgnoreCase(expectedSha1)) throw new IOException("Downloaded file SHA-1 did not match metadata");

            rejectSymlink(destination);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
            return new DownloadResult(destination, total, false);
        } finally {
            connection.disconnect();
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }

    /**
     * Writes already downloaded bytes to cache only when their SHA-1 matches metadata.
     * Intended for a small metadata document whose network response was already verified.
     */
    public DownloadResult storeVerifiedBytes(byte[] content, Path cacheRoot,
                                             String relativePath, String expectedSha1) throws IOException {
        if (content == null) throw new IllegalArgumentException("Content bytes are required");
        validateRelativePath(relativePath);
        validateExpectedMetadata(content.length, expectedSha1);
        String actualSha1 = toHex(sha1Digest().digest(content));
        if (!actualSha1.equalsIgnoreCase(expectedSha1)) {
            throw new IOException("Content SHA-1 did not match metadata");
        }
        if (cacheRoot == null) throw new IllegalArgumentException("Cache root is required");
        Path root = cacheRoot.toAbsolutePath().normalize();
        prepareRoot(root);
        Path destination = root.resolve(relativePath.replace('/', java.io.File.separatorChar)).normalize();
        if (!destination.startsWith(root) || destination.equals(root)) {
            throw new IOException("Resolved cache path escapes the cache root");
        }
        prepareParentDirectories(root, destination.getParent());
        rejectSymlink(destination);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Cache destination exists and is not a regular file");
            }
            if (isVerified(destination, content.length, expectedSha1)) {
                return new DownloadResult(destination, content.length, true);
            }
        }

        Path temporary = Files.createTempFile(destination.getParent(), ".mylauncher-metadata-", ".part");
        try {
            Files.write(temporary, content);
            if (!isVerified(temporary, content.length, expectedSha1)) {
                throw new IOException("Stored metadata failed verification");
            }
            rejectSymlink(destination);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return new DownloadResult(destination, content.length, false);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void checkCancelled(CancellationToken cancellation) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || cancellation.isCancelled()) {
            throw new InterruptedIOException("Minecraft download was cancelled");
        }
    }

    @FunctionalInterface
    public interface TransferProgressListener {
        /** Number of bytes received for the current file so far. */
        void onBytesTransferred(long bytesReceived);
    }

    public static boolean isApprovedDownloadUri(URI uri) {
        if (uri == null || !uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getQuery() != null || uri.getFragment() != null) {
            return false;
        }
        return ALLOWED_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT));
    }

    private static void validateSource(URI source) throws IOException {
        if (!isApprovedDownloadUri(source)) {
            throw new IOException("Refusing to download from an unapproved source URL");
        }
    }

    private static void validateRelativePath(String value) {
        if (value == null || value.isEmpty() || value.startsWith("/") || value.startsWith("\\")
                || value.contains("\\") || value.contains(":") || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Download destination must be a safe relative path");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Download destination must not contain empty or traversal segments");
            }
            for (int i = 0; i < segment.length(); i++) {
                char ch = segment.charAt(i);
                if (ch < 0x20 || ch == 0x7f) throw new IllegalArgumentException("Download destination contains a control character");
            }
        }
    }

    private static void validateExpectedMetadata(long expectedSize, String expectedSha1) {
        if (expectedSize < 0) throw new IllegalArgumentException("Expected download size must not be negative");
        if (expectedSha1 == null || !expectedSha1.matches("[0-9a-fA-F]{40}")) {
            throw new IllegalArgumentException("Expected SHA-1 must contain exactly 40 hexadecimal characters");
        }
    }

    private static void prepareRoot(Path root) throws IOException {
        Path current = root.getRoot();
        for (Path segment : root) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                rejectSymlink(current);
                if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Cache root component is not a directory");
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    private static void prepareParentDirectories(Path root, Path parent) throws IOException {
        if (parent == null || !parent.startsWith(root)) throw new IOException("Cache parent escapes cache root");
        Path current = root;
        for (Path segment : root.relativize(parent)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                rejectSymlink(current);
                if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Cache path component is not a directory");
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    private static void rejectSymlink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Symbolic links are not allowed in download cache paths");
    }

    /** Rechecks a cached file against metadata without opening a network connection. */
    public static boolean isVerifiedFile(Path path, long expectedSize, String expectedSha1) throws IOException {
        validateExpectedMetadata(expectedSize, expectedSha1);
        if (path == null || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        return isVerified(path, expectedSize, expectedSha1);
    }

    private static boolean isVerified(Path path, long expectedSize, String expectedSha1) throws IOException {
        if (Files.size(path) != expectedSize) return false;
        MessageDigest digest = sha1Digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        try (InputStream input = Files.newInputStream(path)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count == 0) continue;
                total += count;
                if (total > expectedSize) return false;
                digest.update(buffer, 0, count);
            }
        }
        return total == expectedSize && toHex(digest.digest()).equalsIgnoreCase(expectedSha1);
    }

    private static MessageDigest sha1Digest() {
        try { return MessageDigest.getInstance("SHA-1"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-1 is required by the Java runtime", exception); }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    public static final class DownloadResult {
        private final Path path;
        private final long sizeBytes;
        private final boolean fromCache;

        private DownloadResult(Path path, long sizeBytes, boolean fromCache) {
            this.path = path;
            this.sizeBytes = sizeBytes;
            this.fromCache = fromCache;
        }
        public Path getPath() { return path; }
        public long getSizeBytes() { return sizeBytes; }
        public boolean isFromCache() { return fromCache; }
    }

    interface ConnectionOpener { FileConnection open(URI uri) throws IOException; }

    interface FileConnection {
        void setConnectTimeout(int timeoutMillis);
        void setReadTimeout(int timeoutMillis);
        void setInstanceFollowRedirects(boolean follow);
        void setRequestMethod(String method) throws IOException;
        void setRequestProperty(String key, String value);
        int getResponseCode() throws IOException;
        long getContentLengthLong();
        InputStream getInputStream() throws IOException;
        void disconnect();
    }

    private static final class JdkFileConnection implements FileConnection {
        private final HttpsURLConnection delegate;
        JdkFileConnection(URI uri) throws IOException {
            if (!isApprovedDownloadUri(uri)) throw new IOException("Refusing to open an unapproved download URL");
            URL url = uri.toURL();
            this.delegate = (HttpsURLConnection) url.openConnection();
        }
        @Override public void setConnectTimeout(int value) { delegate.setConnectTimeout(value); }
        @Override public void setReadTimeout(int value) { delegate.setReadTimeout(value); }
        @Override public void setInstanceFollowRedirects(boolean value) { delegate.setInstanceFollowRedirects(value); }
        @Override public void setRequestMethod(String value) throws IOException { delegate.setRequestMethod(value); }
        @Override public void setRequestProperty(String key, String value) { delegate.setRequestProperty(key, value); }
        @Override public int getResponseCode() throws IOException { return delegate.getResponseCode(); }
        @Override public long getContentLengthLong() { return delegate.getContentLengthLong(); }
        @Override public InputStream getInputStream() throws IOException { return delegate.getInputStream(); }
        @Override public void disconnect() { delegate.disconnect(); }
    }
}
