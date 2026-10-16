package com.mf.mylauncher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable deterministic list of downloads for one Minecraft version installation. */
public final class InstallationPlan {
    private final String versionId;
    private final String assetIndexId;
    private final List<DownloadTask> tasks;
    private final long totalBytes;

    InstallationPlan(String versionId, String assetIndexId, List<DownloadTask> tasks) {
        if (versionId == null || versionId.isEmpty()) throw new IllegalArgumentException("Minecraft version ID is required");
        if (assetIndexId == null || assetIndexId.isEmpty()) throw new IllegalArgumentException("Asset-index ID is required");
        if (tasks == null) throw new IllegalArgumentException("Download tasks are required");
        long total = 0;
        List<DownloadTask> copy = new ArrayList<>();
        for (DownloadTask task : tasks) {
            if (task == null) throw new IllegalArgumentException("Download tasks must not contain null entries");
            try {
                total = Math.addExact(total, task.getSizeBytes());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Installation plan total size exceeds supported range", exception);
            }
            copy.add(task);
        }
        this.versionId = versionId;
        this.assetIndexId = assetIndexId;
        this.tasks = Collections.unmodifiableList(copy);
        this.totalBytes = total;
    }

    public String getVersionId() { return versionId; }
    public String getAssetIndexId() { return assetIndexId; }
    public List<DownloadTask> getTasks() { return tasks; }
    public long getTotalBytes() { return totalBytes; }
    public int getTaskCount() { return tasks.size(); }

    public int count(DownloadTask.Kind kind) {
        int result = 0;
        for (DownloadTask task : tasks) if (task.getKind() == kind) result++;
        return result;
    }
}
