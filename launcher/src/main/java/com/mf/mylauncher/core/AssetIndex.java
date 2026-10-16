package com.mf.mylauncher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Validated, deterministic view of an asset index and its content-addressed downloads. */
public final class AssetIndex {
    private final Map<String, AssetObjectMetadata> objects;
    private final List<AssetObjectMetadata> orderedObjects;
    private final boolean virtual;
    private final boolean mapToResources;
    private final long totalBytes;

    public AssetIndex(Map<String, AssetObjectMetadata> objects, boolean virtual, boolean mapToResources) {
        if (objects == null) throw new IllegalArgumentException("Asset objects map is required");
        Map<String, AssetObjectMetadata> sorted = new TreeMap<>();
        long sizeSum = 0;
        for (Map.Entry<String, AssetObjectMetadata> entry : objects.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Asset objects must have non-null names and metadata");
            }
            if (!entry.getKey().equals(entry.getValue().getName())) {
                throw new IllegalArgumentException("Asset object key and metadata name must match");
            }
            if (sorted.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalArgumentException("Duplicate asset name: " + entry.getKey());
            }
            try {
                sizeSum = Math.addExact(sizeSum, entry.getValue().getSizeBytes());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Total asset size exceeds supported integer range", exception);
            }
        }
        List<AssetObjectMetadata> ordered = new ArrayList<>(sorted.values());
        this.objects = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
        this.orderedObjects = Collections.unmodifiableList(ordered);
        this.virtual = virtual;
        this.mapToResources = mapToResources;
        this.totalBytes = sizeSum;
    }

    public Map<String, AssetObjectMetadata> getObjects() { return objects; }
    public List<AssetObjectMetadata> getOrderedObjects() { return orderedObjects; }
    public boolean isVirtual() { return virtual; }
    public boolean isMapToResources() { return mapToResources; }
    public long getObjectCount() { return orderedObjects.size(); }
    public long getTotalBytes() { return totalBytes; }
}
