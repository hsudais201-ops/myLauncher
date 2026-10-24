package com.mf.mylauncher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Essential per-version client metadata needed by later download and launch stages. */
public final class VersionDetails {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}");
    private static final Pattern SAFE_ASSETS_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}");
    private static final Pattern JAVA_CLASS = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*");

    private final String id;
    private final String mainClass;
    private final String assetsId;
    private final AssetIndexMetadata assetIndex;
    private final DownloadArtifact clientDownload;
    private final int javaMajorVersion;
    private final List<LibraryMetadata> libraries;
    private final VersionArgumentSet arguments;
    private final String legacyMinecraftArguments;
    private final LoggingConfigurationMetadata loggingConfiguration;

    public VersionDetails(String id, String mainClass, String assetsId, AssetIndexMetadata assetIndex,
                          DownloadArtifact clientDownload, int javaMajorVersion,
                          List<LibraryMetadata> libraries) {
        this(id, mainClass, assetsId, assetIndex, clientDownload, javaMajorVersion, libraries,
                VersionArgumentSet.empty(), null);
    }

    public VersionDetails(String id, String mainClass, String assetsId, AssetIndexMetadata assetIndex,
                          DownloadArtifact clientDownload, int javaMajorVersion,
                          List<LibraryMetadata> libraries, VersionArgumentSet arguments,
                          String legacyMinecraftArguments) {
        this(id, mainClass, assetsId, assetIndex, clientDownload, javaMajorVersion,
                libraries, arguments, legacyMinecraftArguments, null);
    }

    public VersionDetails(String id, String mainClass, String assetsId, AssetIndexMetadata assetIndex,
                          DownloadArtifact clientDownload, int javaMajorVersion,
                          List<LibraryMetadata> libraries, VersionArgumentSet arguments,
                          String legacyMinecraftArguments, LoggingConfigurationMetadata loggingConfiguration) {
        if (id == null || !SAFE_ID.matcher(id).matches() || id.contains("..")) {
            throw new IllegalArgumentException("Version-details ID is invalid");
        }
        if (mainClass == null || !JAVA_CLASS.matcher(mainClass).matches()) {
            throw new IllegalArgumentException("Minecraft mainClass must be a fully qualified Java class name");
        }
        if (assetsId == null || !SAFE_ASSETS_ID.matcher(assetsId).matches() || assetsId.contains("..")) {
            throw new IllegalArgumentException("Minecraft assets ID is invalid");
        }
        if (assetIndex == null) throw new IllegalArgumentException("Asset-index metadata is required");
        if (clientDownload == null) throw new IllegalArgumentException("Minecraft client download metadata is required");
        if (javaMajorVersion < 8 || javaMajorVersion > 99) {
            throw new IllegalArgumentException("Required Java major version is outside the supported range");
        }
        if (libraries == null) throw new IllegalArgumentException("Minecraft libraries list is required");
        if (arguments == null) throw new IllegalArgumentException("Minecraft argument metadata is required");
        if (legacyMinecraftArguments != null && legacyMinecraftArguments.length() > 65_536) {
            throw new IllegalArgumentException("Legacy Minecraft arguments exceed the supported length limit");
        }
        List<LibraryMetadata> copy = new ArrayList<>();
        for (LibraryMetadata library : libraries) {
            if (library == null) throw new IllegalArgumentException("Minecraft libraries must not contain null entries");
            copy.add(library);
        }
        this.id = id;
        this.mainClass = mainClass;
        this.assetsId = assetsId;
        this.assetIndex = assetIndex;
        this.clientDownload = clientDownload;
        this.javaMajorVersion = javaMajorVersion;
        this.libraries = Collections.unmodifiableList(copy);
        this.arguments = arguments;
        this.legacyMinecraftArguments = legacyMinecraftArguments;
        this.loggingConfiguration = loggingConfiguration;
    }

    public String getId() { return id; }
    public String getMainClass() { return mainClass; }
    public String getAssetsId() { return assetsId; }
    public AssetIndexMetadata getAssetIndex() { return assetIndex; }
    public DownloadArtifact getClientDownload() { return clientDownload; }
    public int getJavaMajorVersion() { return javaMajorVersion; }
    public List<LibraryMetadata> getLibraries() { return libraries; }
    public VersionArgumentSet getArguments() { return arguments; }
    public String getLegacyMinecraftArguments() { return legacyMinecraftArguments; }
    public java.util.Optional<LoggingConfigurationMetadata> getLoggingConfiguration() {
        return java.util.Optional.ofNullable(loggingConfiguration);
    }
}
