package com.mf.mylauncher.core;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/** A content-addressed asset entry from an asset-index JSON document. */
public final class AssetObjectMetadata {
    private static final Pattern SHA1 = Pattern.compile("[0-9a-fA-F]{40}");
    private final String name;
    private final String hash;
    private final long sizeBytes;

    public AssetObjectMetadata(String name, String hash, long sizeBytes) {
        this.name = validateName(name);
        if (hash == null || !SHA1.matcher(hash).matches()) {
            throw new IllegalArgumentException("Asset object hash must contain exactly 40 hexadecimal characters");
        }
        if (sizeBytes < 0) throw new IllegalArgumentException("Asset object size must not be negative");
        this.hash = hash.toLowerCase(Locale.ROOT);
        this.sizeBytes = sizeBytes;
    }

    private static String validateName(String value) {
        if (value == null || value.isEmpty() || value.length() > 2048
                || value.startsWith("/") || value.startsWith("\\") || value.contains("\\") || value.contains(":")) {
            throw new IllegalArgumentException("Asset object name must be a safe relative path");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Asset object name must not contain empty or traversal path segments");
            }
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 0x20 || ch == 0x7f) throw new IllegalArgumentException("Asset object name contains a control character");
        }
        return value;
    }

    public String getName() { return name; }
    public String getHash() { return hash; }
    public long getSizeBytes() { return sizeBytes; }
    public String getObjectRelativePath() { return "objects/" + hash.substring(0, 2) + "/" + hash; }
    public String getResourceRelativePath() { return name; }
    public URI getDownloadUrl() {
        return URI.create("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash);
    }
}
