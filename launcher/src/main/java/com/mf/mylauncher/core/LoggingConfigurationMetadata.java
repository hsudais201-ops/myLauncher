package com.mf.mylauncher.core;

import java.net.URI;
import java.util.regex.Pattern;

/** Optional client logging XML download declared by Minecraft version metadata. */
public final class LoggingConfigurationMetadata {
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}");

    private final String id;
    private final URI url;
    private final String sha1;
    private final long sizeBytes;

    public LoggingConfigurationMetadata(String id, URI url, String sha1, long sizeBytes) {
        if (id == null || !SAFE_FILE_NAME.matcher(id).matches() || id.contains("..")) {
            throw new IllegalArgumentException("Logging configuration ID must be a safe file name");
        }
        DownloadArtifact validated = new DownloadArtifact(url, sha1, sizeBytes, null);
        this.id = id;
        this.url = validated.getUrl();
        this.sha1 = validated.getSha1();
        this.sizeBytes = validated.getSizeBytes();
    }

    public String getId() { return id; }
    public URI getUrl() { return url; }
    public String getSha1() { return sha1; }
    public long getSizeBytes() { return sizeBytes; }
}
