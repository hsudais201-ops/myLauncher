package com.mf.mylauncher.core;

/** Conservative heap defaults for small Android devices. Values are in MiB. */
public final class MemoryDefaults {
    private MemoryDefaults() {
    }

    public static int suggestedHeapMiB(long totalRamMiB) {
        if (totalRamMiB <= 0) {
            throw new IllegalArgumentException("Total device RAM must be greater than zero");
        }
        if (totalRamMiB < 1024) return 296;
        if (totalRamMiB < 1536) return 448;
        if (totalRamMiB <= 2048) return 512;
        return (int) Math.min(1024L, Math.max(512L, totalRamMiB / 4L));
    }

    /** Keeps the suggestion within the runtime heap limit reported by the device. */
    public static int capToRuntimeMaximum(int suggestedHeapMiB, int runtimeMaximumMiB) {
        if (suggestedHeapMiB <= 0 || runtimeMaximumMiB <= 0) {
            throw new IllegalArgumentException("Heap values must be greater than zero");
        }
        return Math.min(suggestedHeapMiB, runtimeMaximumMiB);
    }
}
