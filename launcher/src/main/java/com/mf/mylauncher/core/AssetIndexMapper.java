package com.mf.mylauncher.core;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps the JSON-decoded asset-index object graph to validated asset objects. */
public final class AssetIndexMapper {
    private AssetIndexMapper() { }

    public static AssetIndex fromDecodedJson(Map<String, ?> root) {
        if (root == null) throw new IllegalArgumentException("Asset-index root must be a JSON object");
        Object rawObjects = root.get("objects");
        if (!(rawObjects instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Asset-index objects must be a JSON object");
        }
        boolean virtual = optionalBoolean(root.get("virtual"), false, "virtual");
        boolean mapToResources = optionalBoolean(root.get("map_to_resources"), false, "map_to_resources");
        Map<String, AssetObjectMetadata> objects = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawObjects).entrySet()) {
            if (!(entry.getKey() instanceof String)) throw new IllegalArgumentException("Asset object names must be strings");
            String name = (String) entry.getKey();
            Map<?, ?> object = requiredMap(entry.getValue(), "objects." + name);
            String hash = requiredString(object.get("hash"), "objects." + name + ".hash");
            long size = requiredLong(object.get("size"), "objects." + name + ".size");
            objects.put(name, new AssetObjectMetadata(name, hash, size));
        }
        return new AssetIndex(objects, virtual, mapToResources);
    }

    private static Map<?, ?> requiredMap(Object value, String field) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException(field + " must be a JSON object");
        return (Map<?, ?>) value;
    }

    private static String requiredString(Object value, String field) {
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must be a non-empty string");
        }
        return (String) value;
    }

    private static boolean optionalBoolean(Object value, boolean defaultValue, String field) {
        if (value == null) return defaultValue;
        if (!(value instanceof Boolean)) throw new IllegalArgumentException(field + " must be a boolean");
        return (Boolean) value;
    }

    private static long requiredLong(Object value, String field) {
        if (!(value instanceof Number)) throw new IllegalArgumentException(field + " must be a number");
        try {
            if (value instanceof BigDecimal) return ((BigDecimal) value).longValueExact();
            long result = ((Number) value).longValue();
            if (Double.isFinite(((Number) value).doubleValue()) && ((Number) value).doubleValue() == result) return result;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(field + " must be an integer in range", exception);
        }
        throw new IllegalArgumentException(field + " must be an integer in range");
    }
}
