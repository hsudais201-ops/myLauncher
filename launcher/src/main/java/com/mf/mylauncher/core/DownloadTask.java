package com.mf.mylauncher.core;

import java.net.URI;
import java.util.Objects;

/** One integrity-verified download in a Minecraft installation plan. */
public final class DownloadTask {
    public enum Kind { CLIENT, LIBRARY, ASSET, LOGGING }
    public enum Destination { GAME_DIRECTORY, ASSET_DIRECTORY }

    private final Kind kind;
    private final Destination destination;
    private final URI source;
    private final String relativePath;
    private final long sizeBytes;
    private final String sha1;

    public DownloadTask(Kind kind, Destination destination, URI source,
                        String relativePath, long sizeBytes, String sha1) {
        this.kind = Objects.requireNonNull(kind, "download kind");
        this.destination = Objects.requireNonNull(destination, "download destination");
        if (source == null || !VerifiedFileDownloader.isApprovedDownloadUri(source)) {
            throw new IllegalArgumentException("Download task source must use an approved HTTPS host");
        }
        validateRelativePath(relativePath);
        if (sizeBytes < 0) throw new IllegalArgumentException("Download task size must not be negative");
        if (sha1 == null || !sha1.matches("[0-9a-fA-F]{40}")) {
            throw new IllegalArgumentException("Download task SHA-1 must contain exactly 40 hexadecimal characters");
        }
        if ((kind == Kind.ASSET || kind == Kind.LOGGING) && destination != Destination.ASSET_DIRECTORY) {
            throw new IllegalArgumentException("Asset and logging downloads must target the asset directory");
        }
        if (kind != Kind.ASSET && kind != Kind.LOGGING && destination != Destination.GAME_DIRECTORY) {
            throw new IllegalArgumentException("Client/library downloads must target the game directory");
        }
        this.source = source;
        this.relativePath = relativePath;
        this.sizeBytes = sizeBytes;
        this.sha1 = sha1.toLowerCase(java.util.Locale.ROOT);
    }

    private static void validateRelativePath(String path) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.startsWith("\\")
                || path.contains("\\") || path.contains(":") || path.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Download task path must be a safe relative path");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Download task path contains an unsafe segment");
            }
        }
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (ch < 0x20 || ch == 0x7f) {
                throw new IllegalArgumentException("Download task path contains a control character");
            }
        }
    }

    public Kind getKind() { return kind; }
    public Destination getDestination() { return destination; }
    public URI getSource() { return source; }
    public String getRelativePath() { return relativePath; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha1() { return sha1; }

    public String getDestinationKey() { return destination.name() + ":" + relativePath; }

    public boolean hasSamePayload(DownloadTask other) {
        return other != null && source.equals(other.source) && sizeBytes == other.sizeBytes
                && sha1.equals(other.sha1) && kind == other.kind && destination == other.destination
                && relativePath.equals(other.relativePath);
    }
}
