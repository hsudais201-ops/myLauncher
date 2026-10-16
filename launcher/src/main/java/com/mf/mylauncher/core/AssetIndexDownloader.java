package com.mf.mylauncher.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Downloads and integrity-checks the per-version asset-index document before decoding it. */
public final class AssetIndexDownloader {
    private final VersionManifestDownloader network;

    public AssetIndexDownloader() {
        this(new VersionManifestDownloader());
    }

    AssetIndexDownloader(VersionManifestDownloader network) {
        if (network == null) throw new IllegalArgumentException("Manifest network client is required");
        this.network = network;
    }

    public AssetIndex download(AssetIndexMetadata metadata) throws IOException {
        return downloadDocument(metadata).getIndex();
    }

    /** Downloads, verifies and parses the document while retaining the exact JSON for assets/indexes. */
    public DownloadedAssetIndex downloadDocument(AssetIndexMetadata metadata) throws IOException {
        if (metadata == null) throw new IllegalArgumentException("Asset-index metadata is required");
        String json = network.downloadMetadata(metadata.getUrl());
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length != metadata.getSizeBytes()) {
            throw new IOException("Asset-index JSON size did not match version metadata");
        }
        String actualSha1 = sha1(bytes);
        if (!actualSha1.equalsIgnoreCase(metadata.getSha1())) {
            throw new IOException("Asset-index JSON SHA-1 did not match version metadata");
        }
        try {
            AssetIndex index = AssetIndexJsonDecoder.decode(json);
            return new DownloadedAssetIndex(json, index);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Minecraft asset-index JSON was invalid", exception);
        }
    }

    public static final class DownloadedAssetIndex {
        private final String json;
        private final AssetIndex index;

        private DownloadedAssetIndex(String json, AssetIndex index) {
            this.json = json;
            this.index = index;
        }

        public String getJson() { return json; }
        public AssetIndex getIndex() { return index; }
    }

    private static String sha1(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(bytes);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is required by the Java runtime", exception);
        }
    }
}
