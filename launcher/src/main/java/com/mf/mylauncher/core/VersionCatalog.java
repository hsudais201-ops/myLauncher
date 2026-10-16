package com.mf.mylauncher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Validated, newest-first index of version metadata returned by Mojang. */
public final class VersionCatalog {
    private final List<MinecraftVersion> versionsNewestFirst;
    private final Map<String, MinecraftVersion> versionsById;

    public VersionCatalog(List<MinecraftVersion> versions) {
        if (versions == null) {
            throw new IllegalArgumentException("Version list is required");
        }
        Map<String, MinecraftVersion> byId = new HashMap<>();
        List<MinecraftVersion> sorted = new ArrayList<>();
        for (MinecraftVersion version : versions) {
            if (version == null) {
                throw new IllegalArgumentException("Version list must not contain null entries");
            }
            if (byId.putIfAbsent(version.getId(), version) != null) {
                throw new IllegalArgumentException("Duplicate Minecraft version ID: " + version.getId());
            }
            sorted.add(version);
        }
        Comparator<MinecraftVersion> newestFirst = Comparator
                .comparing(MinecraftVersion::getReleaseTime, Comparator.reverseOrder())
                .thenComparing(MinecraftVersion::getId);
        sorted.sort(newestFirst);
        this.versionsNewestFirst = Collections.unmodifiableList(sorted);
        this.versionsById = Collections.unmodifiableMap(byId);
    }

    public List<MinecraftVersion> getVersionsNewestFirst() { return versionsNewestFirst; }

    public Optional<MinecraftVersion> findById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(versionsById.get(id));
    }

    public Optional<MinecraftVersion> getLatestRelease() {
        for (MinecraftVersion version : versionsNewestFirst) {
            if (version.isRelease()) return Optional.of(version);
        }
        return Optional.empty();
    }

    public Optional<MinecraftVersion> getLatestSnapshot() {
        for (MinecraftVersion version : versionsNewestFirst) {
            if ("snapshot".equals(version.getType())) return Optional.of(version);
        }
        return Optional.empty();
    }

    public List<MinecraftVersion> getReleasesNewestFirst() {
        List<MinecraftVersion> releases = new ArrayList<>();
        for (MinecraftVersion version : versionsNewestFirst) {
            if (version.isRelease()) releases.add(version);
        }
        return Collections.unmodifiableList(releases);
    }

    public int size() { return versionsNewestFirst.size(); }
}
