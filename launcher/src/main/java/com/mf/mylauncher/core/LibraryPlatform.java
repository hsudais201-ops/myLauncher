package com.mf.mylauncher.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Library-rule evaluation target; this describes rule selection, not native runtime compatibility. */
public final class LibraryPlatform {
    private final String osName;
    private final String architecture;
    private final String osVersion;
    private final Map<String, Boolean> features;

    public LibraryPlatform(String osName, String architecture, String osVersion, Map<String, Boolean> features) {
        if (osName == null || osName.isEmpty()) throw new IllegalArgumentException("OS name is required");
        if (architecture == null || architecture.isEmpty()) throw new IllegalArgumentException("OS architecture is required");
        this.osName = osName;
        this.architecture = architecture;
        this.osVersion = osVersion;
        Map<String, Boolean> copy = new LinkedHashMap<>();
        if (features != null) {
            for (Map.Entry<String, Boolean> entry : features.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isEmpty() || entry.getValue() == null) {
                    throw new IllegalArgumentException("Platform features must have non-empty names and boolean values");
                }
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        this.features = Collections.unmodifiableMap(copy);
    }

    public String getOsName() { return osName; }
    public String getArchitecture() { return architecture; }
    public String getOsVersion() { return osVersion; }
    public Map<String, Boolean> getFeatures() { return features; }
}
