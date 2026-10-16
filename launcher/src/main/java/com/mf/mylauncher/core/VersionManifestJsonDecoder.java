package com.mf.mylauncher.core;

import java.util.Map;

/** End-to-end JSON text to validated Minecraft version manifest decoder. */
public final class VersionManifestJsonDecoder {
    private VersionManifestJsonDecoder() { }

    public static VersionManifest decode(String json) {
        Object decoded = JsonParser.parse(json);
        if (!(decoded instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Minecraft version manifest root must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, ?> root = (Map<String, ?>) decoded;
        return VersionManifestMapper.fromDecodedJson(root);
    }
}
