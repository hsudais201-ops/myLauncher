package com.mf.mylauncher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A selected native library archive and its metadata-safe extraction exclusions. */
public final class NativeLibraryArchive {
    private final String libraryName;
    private final DownloadArtifact artifact;
    private final String relativeLibraryPath;
    private final List<String> extractionExcludes;

    NativeLibraryArchive(String libraryName, DownloadArtifact artifact, List<String> extractionExcludes) {
        if (libraryName == null || libraryName.isEmpty()) throw new IllegalArgumentException("Native library name is required");
        if (artifact == null || !artifact.hasPath()) throw new IllegalArgumentException("Native archive must have a destination path");
        this.libraryName = libraryName;
        this.artifact = artifact;
        this.relativeLibraryPath = "libraries/" + artifact.getPath();
        this.extractionExcludes = Collections.unmodifiableList(new ArrayList<>(extractionExcludes));
    }

    public String getLibraryName() { return libraryName; }
    public DownloadArtifact getArtifact() { return artifact; }
    public String getRelativeLibraryPath() { return relativeLibraryPath; }
    public List<String> getExtractionExcludes() { return extractionExcludes; }

    boolean hasSameArchive(NativeLibraryArchive other) {
        return other != null
                && relativeLibraryPath.equals(other.relativeLibraryPath)
                && artifact.getUrl().equals(other.artifact.getUrl())
                && artifact.getSha1().equals(other.artifact.getSha1())
                && artifact.getSizeBytes() == other.artifact.getSizeBytes()
                && extractionExcludes.equals(other.extractionExcludes);
    }
}
