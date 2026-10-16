package com.mf.mylauncher.core;

/** Immutable installer progress snapshot; byte counts represent verified-file plan downloads. */
public final class InstallationProgress {
    public enum Stage {
        FETCHING_VERSION_METADATA,
        FETCHING_ASSET_INDEX,
        PLANNING,
        DOWNLOADING,
        COMPLETED
    }

    private final Stage stage;
    private final String currentTask;
    private final int completedTasks;
    private final int totalTasks;
    private final long completedBytes;
    private final long totalBytes;

    public InstallationProgress(Stage stage, String currentTask, int completedTasks, int totalTasks,
                               long completedBytes, long totalBytes) {
        if (stage == null) throw new IllegalArgumentException("Progress stage is required");
        if (completedTasks < 0 || totalTasks < completedTasks) throw new IllegalArgumentException("Task progress is invalid");
        if (completedBytes < 0 || totalBytes < completedBytes) throw new IllegalArgumentException("Byte progress is invalid");
        this.stage = stage;
        this.currentTask = currentTask;
        this.completedTasks = completedTasks;
        this.totalTasks = totalTasks;
        this.completedBytes = completedBytes;
        this.totalBytes = totalBytes;
    }

    public Stage getStage() { return stage; }
    public String getCurrentTask() { return currentTask; }
    public int getCompletedTasks() { return completedTasks; }
    public int getTotalTasks() { return totalTasks; }
    public long getCompletedBytes() { return completedBytes; }
    public long getTotalBytes() { return totalBytes; }

    public int getPercent() {
        if (stage == Stage.COMPLETED) return 100;
        if (totalBytes == 0) return totalTasks == 0 ? 0 : (int) ((completedTasks * 100L) / totalTasks);
        return (int) Math.min(100L, (completedBytes * 100L) / totalBytes);
    }
}
