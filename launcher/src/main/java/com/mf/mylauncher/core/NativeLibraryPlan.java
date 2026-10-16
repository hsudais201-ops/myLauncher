package com.mf.mylauncher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable selection of native archives for an explicitly requested native target. */
public final class NativeLibraryPlan {
    private final String versionId;
    private final String nativeOsName;
    private final String architectureToken;
    private final List<NativeLibraryArchive> archives;
    private final long totalBytes;

    NativeLibraryPlan(String versionId, String nativeOsName, String architectureToken,
                      List<NativeLibraryArchive> archives) {
        if (versionId == null || versionId.isEmpty()) throw new IllegalArgumentException("Minecraft version ID is required");
        if (nativeOsName == null || nativeOsName.isEmpty()) throw new IllegalArgumentException("Native OS name is required");
        if (architectureToken == null || architectureToken.isEmpty()) throw new IllegalArgumentException("Native architecture token is required");
        if (archives == null) throw new IllegalArgumentException("Native archives list is required");
        List<NativeLibraryArchive> copy = new ArrayList<>();
        long sum = 0;
        for (NativeLibraryArchive archive : archives) {
            if (archive == null) throw new IllegalArgumentException("Native archives must not contain null entries");
            try {
                sum = Math.addExact(sum, archive.getArtifact().getSizeBytes());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Native download size exceeds supported integer range", exception);
            }
            copy.add(archive);
        }
        this.versionId = versionId;
        this.nativeOsName = nativeOsName;
        this.architectureToken = architectureToken;
        this.archives = Collections.unmodifiableList(copy);
        this.totalBytes = sum;
    }

    public String getVersionId() { return versionId; }
    public String getNativeOsName() { return nativeOsName; }
    public String getArchitectureToken() { return architectureToken; }
    public List<NativeLibraryArchive> getArchives() { return archives; }
    public int getArchiveCount() { return archives.size(); }
    public long getTotalBytes() { return totalBytes; }
}
