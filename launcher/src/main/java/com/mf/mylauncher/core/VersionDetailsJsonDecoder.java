package com.mf.mylauncher.core;

import java.util.Map;

/** Converts the downloaded per-version JSON text into validated runtime/download metadata. */
public final class VersionDetailsJsonDecoder {
    private VersionDetailsJsonDecoder() { }

    public static VersionDetails decode(String json) {
        return decode(json, null);
    }

    public static VersionDetails decode(String json, String expectedVersionId) {
        Object decoded = JsonParser.parse(json);
        if (!(decoded instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Minecraft per-version metadata root must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, ?> root = (Map<String, ?>) decoded;
        return VersionDetailsMapper.fromDecodedJson(root, expectedVersionId);
    }
}
