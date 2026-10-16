package com.mf.mylauncher.core;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

/** Downloads and validates a specific version's client/library/runtime metadata. */
public final class VersionDetailsDownloader {
    private final VersionManifestDownloader network;

    public VersionDetailsDownloader() {
        this(new VersionManifestDownloader());
    }

    VersionDetailsDownloader(VersionManifestDownloader network) {
        this.network = Objects.requireNonNull(network, "manifest downloader");
    }

    public VersionDetails download(MinecraftVersion version) throws IOException {
        if (version == null) throw new IllegalArgumentException("Minecraft version is required");
        URI metadataUrl = version.getMetadataUrl();
        String json = network.downloadMetadata(metadataUrl);
        try {
            return VersionDetailsJsonDecoder.decode(json, version.getId());
        } catch (IllegalArgumentException exception) {
            throw new IOException("Minecraft version metadata was invalid for " + version.getId(), exception);
        }
    }
}
