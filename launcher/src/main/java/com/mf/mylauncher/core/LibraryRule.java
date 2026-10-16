package com.mf.mylauncher.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** One ordered Minecraft library allow/disallow rule. Later matching rules override earlier ones. */
public final class LibraryRule {
    private final boolean allow;
    private final String osName;
    private final String osArch;
    private final Pattern osVersion;
    private final Map<String, Boolean> features;

    public LibraryRule(boolean allow, String osName, String osArch, String osVersionRegex,
                       Map<String, Boolean> features) {
        this.allow = allow;
        this.osName = emptyToNull(osName);
        this.osArch = emptyToNull(osArch);
        try {
            this.osVersion = osVersionRegex == null || osVersionRegex.isEmpty()
                    ? null : Pattern.compile(osVersionRegex);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException("Library rule OS-version regex is invalid", exception);
        }
        Map<String, Boolean> featureCopy = new LinkedHashMap<>();
        if (features != null) {
            for (Map.Entry<String, Boolean> entry : features.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isEmpty() || entry.getValue() == null) {
                    throw new IllegalArgumentException("Library rule features must have names and boolean values");
                }
                featureCopy.put(entry.getKey(), entry.getValue());
            }
        }
        this.features = Collections.unmodifiableMap(featureCopy);
    }

    private static String emptyToNull(String value) { return value == null || value.isEmpty() ? null : value; }

    public boolean isAllow() { return allow; }
    public String getOsName() { return osName; }
    public String getOsArch() { return osArch; }
    public Map<String, Boolean> getFeatures() { return features; }

    public boolean matches(String actualOsName, String actualOsArch, String actualOsVersion,
                           Map<String, Boolean> actualFeatures) {
        if (osName != null && !osName.equals(actualOsName)) return false;
        if (osArch != null && !osArch.equals(actualOsArch)) return false;
        if (osVersion != null && (actualOsVersion == null || !osVersion.matcher(actualOsVersion).matches())) return false;
        Map<String, Boolean> safeFeatures = actualFeatures == null ? Collections.emptyMap() : actualFeatures;
        for (Map.Entry<String, Boolean> entry : features.entrySet()) {
            if (!entry.getValue().equals(safeFeatures.get(entry.getKey()))) return false;
        }
        return true;
    }

    /** No rules means allowed; with rules, the last matching action determines the result. */
    public static boolean isAllowed(Iterable<LibraryRule> rules, String osName, String osArch,
                                    String osVersion, Map<String, Boolean> features) {
        if (rules == null) throw new IllegalArgumentException("Library rules must not be null");
        boolean hasRules = false;
        boolean matchedAnyRule = false;
        boolean allowed = false;
        for (LibraryRule rule : rules) {
            hasRules = true;
            if (rule == null) throw new IllegalArgumentException("Library rules must not contain null entries");
            if (rule.matches(osName, osArch, osVersion, features)) {
                matchedAnyRule = true;
                allowed = rule.allow;
            }
        }
        return !hasRules || (matchedAnyRule && allowed);
    }
}
