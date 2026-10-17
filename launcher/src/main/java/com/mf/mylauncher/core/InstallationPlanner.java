package com.mf.mylauncher.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Builds a stable download plan from already validated Mojang version and asset metadata. */
public final class InstallationPlanner {
    private InstallationPlanner() { }

    public static InstallationPlan create(VersionDetails version, AssetIndex assetIndex, LibraryPlatform platform) {
        return create(version, assetIndex, platform, null);
    }

    /** Includes native archive downloads only when a native target has been explicitly selected. */
    public static InstallationPlan create(VersionDetails version, AssetIndex assetIndex, LibraryPlatform platform,
                                          NativeLibraryPlan nativePlan) {
        if (version == null) throw new IllegalArgumentException("Version details are required");
        if (assetIndex == null) throw new IllegalArgumentException("Asset index is required");
        if (platform == null) throw new IllegalArgumentException("Library platform is required");

        Map<String, DownloadTask> uniqueTasks = new LinkedHashMap<>();
        String clientPath = "versions/" + version.getId() + "/" + version.getId() + ".jar";
        add(uniqueTasks, new DownloadTask(DownloadTask.Kind.CLIENT, DownloadTask.Destination.GAME_DIRECTORY,
                version.getClientDownload().getUrl(), clientPath,
                version.getClientDownload().getSizeBytes(), version.getClientDownload().getSha1()));

        version.getLoggingConfiguration().ifPresent(logging -> add(uniqueTasks,
                new DownloadTask(DownloadTask.Kind.LOGGING, DownloadTask.Destination.ASSET_DIRECTORY,
                        logging.getUrl(), "log_configs/" + logging.getId(),
                        logging.getSizeBytes(), logging.getSha1())));

        if (nativePlan != null) {
            if (!version.getId().equals(nativePlan.getVersionId())) {
                throw new IllegalArgumentException("Native library plan version does not match version metadata");
            }
            NativeLibraryPlan verifiedSelection = NativeLibraryPlanner.create(version, platform,
                    nativePlan.getNativeOsName(), nativePlan.getArchitectureToken());
            if (!verifiedSelection.hasSameSelectionAs(nativePlan)) {
                throw new IllegalArgumentException("Native library plan does not match current version metadata/platform rules");
            }
            for (NativeLibraryArchive archive : nativePlan.getArchives()) {
                DownloadArtifact artifact = archive.getArtifact();
                add(uniqueTasks, new DownloadTask(DownloadTask.Kind.NATIVE,
                        DownloadTask.Destination.GAME_DIRECTORY, artifact.getUrl(),
                        archive.getRelativeLibraryPath(), artifact.getSizeBytes(), artifact.getSha1()));
            }
        }

        for (LibraryMetadata library : version.getLibraries()) {
            if (!library.isAllowedOn(platform.getOsName(), platform.getArchitecture(),
                    platform.getOsVersion(), platform.getFeatures())) continue;
            if (!library.isCompatibleWithPlatform(platform)) continue;
            Optional<DownloadArtifact> artifact = library.getArtifact();
            if (!artifact.isPresent()) {
                // Native-only classifiers need Android runtime-specific selection/extraction and are not plain JAR tasks.
                continue;
            }
            DownloadArtifact download = artifact.get();
            if (!download.hasPath()) throw new IllegalArgumentException("Library artifact path is missing for " + library.getName());
            add(uniqueTasks, new DownloadTask(DownloadTask.Kind.LIBRARY, DownloadTask.Destination.GAME_DIRECTORY,
                    download.getUrl(), "libraries/" + download.getPath(), download.getSizeBytes(), download.getSha1()));
        }

        for (AssetObjectMetadata asset : assetIndex.getOrderedObjects()) {
            add(uniqueTasks, new DownloadTask(DownloadTask.Kind.ASSET, DownloadTask.Destination.ASSET_DIRECTORY,
                    asset.getDownloadUrl(), asset.getObjectRelativePath(), asset.getSizeBytes(), asset.getHash()));
        }

        List<DownloadTask> tasks = new ArrayList<>(uniqueTasks.values());
        tasks.sort(Comparator.comparing((DownloadTask task) -> task.getDestination().ordinal())
                .thenComparing(DownloadTask::getRelativePath)
                .thenComparing(task -> task.getKind().ordinal()));
        return new InstallationPlan(version.getId(), version.getAssetIndex().getId(), tasks);
    }

    private static void add(Map<String, DownloadTask> tasks, DownloadTask task) {
        DownloadTask existing = tasks.putIfAbsent(task.getDestinationKey(), task);
        if (existing != null && !existing.hasSamePayload(task)) {
            throw new IllegalArgumentException("Conflicting downloads target the same cache path: " + task.getDestinationKey());
        }
    }
}
