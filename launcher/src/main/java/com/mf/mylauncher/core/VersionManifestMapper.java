package com.mf.mylauncher.core;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Maps a JSON-decoded object graph to validated launcher domain objects. */
public final class VersionManifestMapper {
    private VersionManifestMapper() {
    }

    /**
     * Accepts the Map/List object graph produced by a JSON decoder (for example Gson's Map adapter).
     * Keeping JSON-library calls at the Android boundary makes the core easy to test without an SDK.
     */
    public static VersionManifest fromDecodedJson(Map<String, ?> root) {
        if (root == null) throw new IllegalArgumentException("Manifest root must be a JSON object");
        Map<?, ?> latest = requiredMap(root.get("latest"), "latest");
        String latestReleaseId = requiredString(latest.get("release"), "latest.release");
        String latestSnapshotId = requiredString(latest.get("snapshot"), "latest.snapshot");
        Object rawVersions = root.get("versions");
        if (!(rawVersions instanceof List<?>)) {
            throw new IllegalArgumentException("versions must be a JSON array");
        }

        List<MinecraftVersion> versions = new ArrayList<>();
        int index = 0;
        for (Object rawVersion : (List<?>) rawVersions) {
            String prefix = "versions[" + index + "]";
            Map<?, ?> item = requiredMap(rawVersion, prefix);
            String id = requiredString(item.get("id"), prefix + ".id");
            String type = requiredString(item.get("type"), prefix + ".type");
            String url = requiredString(item.get("url"), prefix + ".url");
            String releaseTime = requiredString(item.get("releaseTime"), prefix + ".releaseTime");
            try {
                versions.add(new MinecraftVersion(id, type, URI.create(url), releaseTime));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid version metadata at " + prefix + ": " + exception.getMessage(), exception);
            }
            index++;
        }

        VersionCatalog catalog = new VersionCatalog(versions);
        return new VersionManifest(latestReleaseId, latestSnapshotId, catalog);
    }

    private static Map<?, ?> requiredMap(Object value, String field) {
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(field + " must be a JSON object");
        }
        return (Map<?, ?>) value;
    }

    private static String requiredString(Object value, String field) {
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must be a non-empty string");
        }
        return (String) value;
    }
}
