package com.mf.mylauncher.core;

import java.net.URI;
import java.util.regex.Pattern;

/** Identity and integrity metadata for Minecraft's asset-index JSON file. */
public final class AssetIndexMetadata {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}");
    private final String id;
    private final DownloadArtifact artifact;
    private final long totalSizeBytes;

    public AssetIndexMetadata(String id, URI url, String sha1, long sizeBytes, long totalSizeBytes) {
        if (id == null || !SAFE_ID.matcher(id).matches() || id.contains("..")) {
            throw new IllegalArgumentException("Asset index ID is invalid");
        }
        if (sizeBytes <= 0 || totalSizeBytes < 0) {
            throw new IllegalArgumentException("Asset index sizes are invalid");
        }
        if (totalSizeBytes > 0 && totalSizeBytes < sizeBytes) {
            throw new IllegalArgumentException("Asset index total size cannot be smaller than index file size");
        }
        this.id = id;
        this.artifact = new DownloadArtifact(url, sha1, sizeBytes, null);
        this.totalSizeBytes = totalSizeBytes;
    }

    public String getId() { return id; }
    public URI getUrl() { return artifact.getUrl(); }
    public String getSha1() { return artifact.getSha1(); }
    public long getSizeBytes() { return artifact.getSizeBytes(); }
    public long getTotalSizeBytes() { return totalSizeBytes; }
}
