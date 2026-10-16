package com.mf.mylauncher.core;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/** A verified-download descriptor from Minecraft version metadata. */
public final class DownloadArtifact {
    private static final Pattern SHA1 = Pattern.compile("[0-9a-fA-F]{40}");
    private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z0-9._+-]+(?:/[A-Za-z0-9._+-]+)*");

    private final URI url;
    private final String sha1;
    private final long sizeBytes;
    private final String path;

    public DownloadArtifact(URI url, String sha1, long sizeBytes, String path) {
        this.url = validateUrl(url);
        this.sha1 = validateSha1(sha1);
        if (sizeBytes <= 0) throw new IllegalArgumentException("Artifact size must be greater than zero");
        this.sizeBytes = sizeBytes;
        this.path = path == null ? null : validatePath(path);
    }

    private static URI validateUrl(URI value) {
        if (value == null || !value.isAbsolute() || !"https".equalsIgnoreCase(value.getScheme())
                || value.getHost() == null || value.getUserInfo() != null) {
            throw new IllegalArgumentException("Artifact URL must be an absolute HTTPS URL without user info");
        }
        return value;
    }

    private static String validateSha1(String value) {
        if (value == null || !SHA1.matcher(value).matches()) {
            throw new IllegalArgumentException("Artifact SHA-1 must contain exactly 40 hexadecimal characters");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String validatePath(String value) {
        if (value.isEmpty() || value.startsWith("/") || value.startsWith("\\")
                || value.contains("\\") || !SAFE_PATH.matcher(value).matches()) {
            throw new IllegalArgumentException("Artifact path must be a safe relative slash-separated path");
        }
        for (String segment : value.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Artifact path must not traverse parent directories");
            }
        }
        return value;
    }

    public URI getUrl() { return url; }
    public String getSha1() { return sha1; }
    public long getSizeBytes() { return sizeBytes; }
    public String getPath() { return path; }
    public boolean hasPath() { return path != null; }
}
