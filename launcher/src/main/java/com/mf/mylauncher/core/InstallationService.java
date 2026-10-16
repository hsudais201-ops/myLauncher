package com.mf.mylauncher.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

/** Orchestrates metadata validation, asset-index storage, planning and checksum-verified downloads. */
public final class InstallationService {
    private final VersionDetailsDownloader versionDownloader;
    private final AssetIndexDownloader assetIndexDownloader;
    private final VerifiedFileDownloader fileDownloader;

    public InstallationService() {
        this(new VersionDetailsDownloader(), new AssetIndexDownloader(), new VerifiedFileDownloader());
    }

    InstallationService(VersionDetailsDownloader versionDownloader, AssetIndexDownloader assetIndexDownloader,
                        VerifiedFileDownloader fileDownloader) {
        this.versionDownloader = Objects.requireNonNull(versionDownloader, "version downloader");
        this.assetIndexDownloader = Objects.requireNonNull(assetIndexDownloader, "asset index downloader");
        this.fileDownloader = Objects.requireNonNull(fileDownloader, "verified file downloader");
    }

    public InstallationResult install(MinecraftVersion selectedVersion, Path gameDirectory,
                                      LibraryPlatform platform) throws IOException {
        return install(selectedVersion, gameDirectory, platform, CancellationToken.NONE, progress -> { });
    }

    /**
     * Installs all supported client/library/asset tasks in the plan.
     * Cancellation is checked between metadata steps and tasks, and during active file transfers.
     */
    public InstallationResult install(MinecraftVersion selectedVersion, Path gameDirectory,
                                      LibraryPlatform platform, CancellationToken cancellation,
                                      ProgressListener listener) throws IOException {
        return install(selectedVersion, gameDirectory, platform, null, cancellation, listener);
    }

    /** Installs base files and extracts native archives only when a compatible target plan is supplied. */
    public InstallationResult install(MinecraftVersion selectedVersion, Path gameDirectory,
                                      LibraryPlatform platform, NativeLibraryPlan nativePlan,
                                      CancellationToken cancellation, ProgressListener listener) throws IOException {
        if (selectedVersion == null) throw new IllegalArgumentException("Minecraft version is required");
        if (gameDirectory == null) throw new IllegalArgumentException("Game directory is required");
        if (platform == null) throw new IllegalArgumentException("Target library platform is required");
        if (cancellation == null) throw new IllegalArgumentException("Cancellation token is required");
        if (listener == null) throw new IllegalArgumentException("Progress listener is required");

        Path gameRoot = gameDirectory.toAbsolutePath().normalize();
        checkCancelled(cancellation);
        publish(listener, new InstallationProgress(InstallationProgress.Stage.FETCHING_VERSION_METADATA,
                selectedVersion.getId(), 0, 0, 0, 0));
        VersionDetails details = versionDownloader.download(selectedVersion);

        checkCancelled(cancellation);
        publish(listener, new InstallationProgress(InstallationProgress.Stage.FETCHING_ASSET_INDEX,
                details.getAssetIndex().getId(), 0, 0, 0, 0));
        AssetIndexDownloader.DownloadedAssetIndex indexDocument =
                assetIndexDownloader.downloadDocument(details.getAssetIndex());
        checkCancelled(cancellation);

        publish(listener, new InstallationProgress(InstallationProgress.Stage.PLANNING,
                selectedVersion.getId(), 0, 0, 0, 0));
        InstallationPlan plan;
        try {
            plan = InstallationPlanner.create(details, indexDocument.getIndex(), platform, nativePlan);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Minecraft installation metadata could not be planned safely", exception);
        }

        byte[] indexBytes = indexDocument.getJson().getBytes(StandardCharsets.UTF_8);
        Path assetsRoot = gameRoot.resolve("assets");
        VerifiedFileDownloader.DownloadResult storedIndex = fileDownloader.storeVerifiedBytes(
                indexBytes, assetsRoot, "indexes/" + details.getAssetIndex().getId() + ".json",
                details.getAssetIndex().getSha1());

        long completedBytes = 0;
        int completedTasks = 0;
        int downloadedTasks = 0;
        int cachedTasks = 0;
        publish(listener, new InstallationProgress(InstallationProgress.Stage.DOWNLOADING,
                null, 0, plan.getTaskCount(), 0, plan.getTotalBytes()));

        for (DownloadTask task : plan.getTasks()) {
            checkCancelled(cancellation);
            Path root = task.getDestination() == DownloadTask.Destination.ASSET_DIRECTORY ? assetsRoot : gameRoot;
            final long bytesBeforeTask = completedBytes;
            final int tasksBeforeTask = completedTasks;
            publish(listener, new InstallationProgress(InstallationProgress.Stage.DOWNLOADING,
                    task.getRelativePath(), completedTasks, plan.getTaskCount(), completedBytes, plan.getTotalBytes()));
            VerifiedFileDownloader.DownloadResult taskResult = fileDownloader.download(
                    task.getSource(), root, task.getRelativePath(), task.getSizeBytes(), task.getSha1(),
                    cancellation,
                    bytesForTask -> publish(listener, new InstallationProgress(
                            InstallationProgress.Stage.DOWNLOADING, task.getRelativePath(), tasksBeforeTask,
                            plan.getTaskCount(), Math.min(plan.getTotalBytes(), bytesBeforeTask + bytesForTask),
                            plan.getTotalBytes())));
            if (taskResult.isFromCache()) cachedTasks++; else downloadedTasks++;
            completedBytes = Math.addExact(completedBytes, task.getSizeBytes());
            completedTasks++;
            publish(listener, new InstallationProgress(InstallationProgress.Stage.DOWNLOADING,
                    task.getRelativePath(), completedTasks, plan.getTaskCount(), completedBytes, plan.getTotalBytes()));
        }

        checkCancelled(cancellation);
        if (nativePlan != null && nativePlan.getArchiveCount() > 0) {
            publish(listener, new InstallationProgress(InstallationProgress.Stage.EXTRACTING_NATIVES,
                    nativePlan.getNativeOsName() + "/" + nativePlan.getArchitectureToken(),
                    completedTasks, plan.getTaskCount(), completedBytes, plan.getTotalBytes()));
            java.util.List<SafeArchiveExtractor.ArchiveSpec> archives = new java.util.ArrayList<>();
            for (NativeLibraryArchive nativeArchive : nativePlan.getArchives()) {
                checkCancelled(cancellation);
                Path archivePath = gameRoot.resolve(nativeArchive.getRelativeLibraryPath()).normalize();
                if (!archivePath.startsWith(gameRoot) || archivePath.equals(gameRoot)
                        || !VerifiedFileDownloader.isVerifiedFile(archivePath,
                        nativeArchive.getArtifact().getSizeBytes(), nativeArchive.getArtifact().getSha1())) {
                    throw new IOException("Downloaded native archive failed integrity verification: "
                            + nativeArchive.getRelativeLibraryPath());
                }
                archives.add(new SafeArchiveExtractor.ArchiveSpec(archivePath, nativeArchive.getExtractionExcludes()));
            }
            SafeArchiveExtractor.extractAll(archives, gameRoot.resolve("natives").resolve(details.getId()));
        }
        checkCancelled(cancellation);
        publish(listener, new InstallationProgress(InstallationProgress.Stage.MATERIALIZING_ASSETS,
                details.getAssetIndex().getId(), completedTasks, plan.getTaskCount(), completedBytes, plan.getTotalBytes()));
        AssetLayoutMaterializer.materialize(indexDocument.getIndex(), details.getAssetIndex().getId(),
                assetsRoot, gameRoot, cancellation);
        checkCancelled(cancellation);
        publish(listener, new InstallationProgress(InstallationProgress.Stage.COMPLETED,
                null, completedTasks, plan.getTaskCount(), completedBytes, plan.getTotalBytes()));
        return new InstallationResult(details.getId(), gameRoot, storedIndex.getPath(), plan,
                downloadedTasks, cachedTasks);
    }

    private static void checkCancelled(CancellationToken cancellation) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || cancellation.isCancelled()) {
            throw new InterruptedIOException("Minecraft installation was cancelled");
        }
    }

    private static void publish(ProgressListener listener, InstallationProgress progress) {
        listener.onProgress(progress);
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(InstallationProgress progress);
    }
}
