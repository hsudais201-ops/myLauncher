package com.mf.mylauncher.core;

import java.util.Map;

/** Decodes an asset-index JSON string into deterministic, validated asset metadata. */
public final class AssetIndexJsonDecoder {
    private AssetIndexJsonDecoder() { }

    public static AssetIndex decode(String json) {
        Object decoded = JsonParser.parse(json);
        if (!(decoded instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Minecraft asset-index root must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, ?> root = (Map<String, ?>) decoded;
        return AssetIndexMapper.fromDecodedJson(root);
    }
}
