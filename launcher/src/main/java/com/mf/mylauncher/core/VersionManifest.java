package com.mf.mylauncher.core;

/** Validated Mojang version-manifest payload after decoding the JSON transport layer. */
public final class VersionManifest {
    private final String latestReleaseId;
    private final String latestSnapshotId;
    private final VersionCatalog catalog;

    public VersionManifest(String latestReleaseId, String latestSnapshotId, VersionCatalog catalog) {
        if (catalog == null) throw new IllegalArgumentException("Version catalog is required");
        if (latestReleaseId == null || latestReleaseId.trim().isEmpty()) {
            throw new IllegalArgumentException("Latest release ID is required");
        }
        if (latestSnapshotId == null || latestSnapshotId.trim().isEmpty()) {
            throw new IllegalArgumentException("Latest snapshot ID is required");
        }
        MinecraftVersion release = catalog.findById(latestReleaseId)
                .orElseThrow(() -> new IllegalArgumentException("Latest release is missing from the version list: " + latestReleaseId));
        if (!release.isRelease()) {
            throw new IllegalArgumentException("Latest release ID does not reference a release version: " + latestReleaseId);
        }
        MinecraftVersion snapshot = catalog.findById(latestSnapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Latest snapshot is missing from the version list: " + latestSnapshotId));
        if (!"snapshot".equals(snapshot.getType())) {
            throw new IllegalArgumentException("Latest snapshot ID does not reference a snapshot version: " + latestSnapshotId);
        }
        this.latestReleaseId = latestReleaseId;
        this.latestSnapshotId = latestSnapshotId;
        this.catalog = catalog;
    }

    public String getLatestReleaseId() { return latestReleaseId; }
    public String getLatestSnapshotId() { return latestSnapshotId; }
    public MinecraftVersion getLatestRelease() { return catalog.findById(latestReleaseId).get(); }
    public MinecraftVersion getLatestSnapshot() { return catalog.findById(latestSnapshotId).get(); }
    public VersionCatalog getCatalog() { return catalog; }
}
