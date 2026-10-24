package com.voidcraft.cosmetics;

import java.util.Set;

/** Shared allow-list for cosmetic IDs written by the launcher and website handoff. */
public final class CosmeticId {
    private static final Set<String> SUPPORTED = Set.of(
        "void-aura", "neon-mint", "arctic-ice", "solar-ember"
    );

    private CosmeticId() {}

    /** Return a supported ID, or "none" for null, unknown, or malformed input. */
    public static String normalize(String candidate) {
        return candidate != null && SUPPORTED.contains(candidate) ? candidate : "none";
    }

    public static boolean isSupported(String candidate) {
        return candidate != null && SUPPORTED.contains(candidate);
    }
}
