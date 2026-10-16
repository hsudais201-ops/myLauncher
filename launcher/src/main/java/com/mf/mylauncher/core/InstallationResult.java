package com.mf.mylauncher.core;

import java.nio.file.Path;

/** Summary of a fully verified version installation. */
public final class InstallationResult {
    private final String versionId;
    private final Path gameDirectory;
    private final Path assetIndexPath;
    private final InstallationPlan plan;
    private final int downloadedTaskCount;
    private final int cachedTaskCount;

    InstallationResult(String versionId, Path gameDirectory, Path assetIndexPath, InstallationPlan plan,
                       int downloadedTaskCount, int cachedTaskCount) {
        this.versionId = versionId;
        this.gameDirectory = gameDirectory;
        this.assetIndexPath = assetIndexPath;
        this.plan = plan;
        this.downloadedTaskCount = downloadedTaskCount;
        this.cachedTaskCount = cachedTaskCount;
    }

    public String getVersionId() { return versionId; }
    public Path getGameDirectory() { return gameDirectory; }
    public Path getAssetIndexPath() { return assetIndexPath; }
    public InstallationPlan getPlan() { return plan; }
    public int getDownloadedTaskCount() { return downloadedTaskCount; }
    public int getCachedTaskCount() { return cachedTaskCount; }
}
