package com.mf.mylauncher.core;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** One ordered Minecraft allow/disallow rule. Later matching rules override earlier ones. */
public final class LibraryRule {
    private static final Pattern DOTTED_NUMERIC_VERSION = Pattern.compile("[0-9]+(?:\\.[0-9]+)*");
    private static final Pattern DOTTED_NUMERIC_PREFIX = Pattern.compile("^([0-9]+(?:\\.[0-9]+)*)");

    private final boolean allow;
    private final String osName;
    private final String osArch;
    private final Pattern osArchPattern;
    private final Pattern osVersion;
    private final String osVersionMin;
    private final String osVersionMax;
    private final Map<String, Boolean> features;

    /** Legacy Mojang rule constructor: OS version is a regular expression. */
    public LibraryRule(boolean allow, String osName, String osArch, String osVersionRegex,
                       Map<String, Boolean> features) {
        this(allow, osName, osArch, osVersionRegex, null, null, features);
    }

    /**
     * Mojang's newer versionRange uses a numeric [min, max) interval. The maximum is exclusive,
     * allowing adjacent rules to divide OS versions without matching the same boundary twice.
     */
    public LibraryRule(boolean allow, String osName, String osArch, String osVersionRegex,
                       String osVersionMin, String osVersionMax, Map<String, Boolean> features) {
        this.allow = allow;
        this.osName = emptyToNull(osName);
        this.osArch = emptyToNull(osArch);
        try {
            this.osArchPattern = this.osArch == null ? null : Pattern.compile(this.osArch);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException("Library rule OS-architecture regex is invalid", exception);
        }
        try {
            this.osVersion = osVersionRegex == null || osVersionRegex.isEmpty()
                    ? null : Pattern.compile(osVersionRegex);
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException("Library rule OS-version regex is invalid", exception);
        }
        if (this.osVersion != null && (osVersionMin != null || osVersionMax != null)) {
            throw new IllegalArgumentException("OS version regex and versionRange cannot both be specified");
        }
        this.osVersionMin = validateVersionBound(osVersionMin, "min");
        this.osVersionMax = validateVersionBound(osVersionMax, "max");
        if (this.osVersionMin == null && this.osVersionMax == null && (osVersionMin != null || osVersionMax != null)) {
            throw new IllegalArgumentException("OS versionRange must have at least one bound");
        }
        if (this.osVersionMin != null && this.osVersionMax != null
                && compareDottedVersions(this.osVersionMin, this.osVersionMax) >= 0) {
            throw new IllegalArgumentException("OS versionRange minimum must be less than its exclusive maximum");
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

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String validateVersionBound(String value, String label) {
        if (value == null) return null;
        if (value.length() > 128 || !DOTTED_NUMERIC_VERSION.matcher(value).matches()) {
            throw new IllegalArgumentException("OS versionRange " + label + " must be a dotted numeric version");
        }
        return value;
    }

    private static int compareDottedVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int count = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < count; i++) {
            BigInteger leftPart = i < leftParts.length ? new BigInteger(leftParts[i]) : BigInteger.ZERO;
            BigInteger rightPart = i < rightParts.length ? new BigInteger(rightParts[i]) : BigInteger.ZERO;
            int compare = leftPart.compareTo(rightPart);
            if (compare != 0) return compare;
        }
        return 0;
    }

    private static String numericVersionPrefix(String value) {
        if (value == null) return null;
        Matcher matcher = DOTTED_NUMERIC_PREFIX.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    public boolean isAllow() { return allow; }
    public String getOsName() { return osName; }
    public String getOsArch() { return osArch; }
    public String getOsVersionMin() { return osVersionMin; }
    public String getOsVersionMax() { return osVersionMax; }
    public Map<String, Boolean> getFeatures() { return features; }

    public boolean matches(String actualOsName, String actualOsArch, String actualOsVersion,
                           Map<String, Boolean> actualFeatures) {
        if (osName != null && !osName.equals(actualOsName)) return false;
        if (osArchPattern != null && (actualOsArch == null || !osArchPattern.matcher(actualOsArch).matches())) return false;
        if (osVersion != null && (actualOsVersion == null || !osVersion.matcher(actualOsVersion).matches())) return false;
        if (osVersionMin != null || osVersionMax != null) {
            String numericActualVersion = numericVersionPrefix(actualOsVersion);
            if (numericActualVersion == null) return false;
            if (osVersionMin != null && compareDottedVersions(numericActualVersion, osVersionMin) < 0) return false;
            if (osVersionMax != null && compareDottedVersions(numericActualVersion, osVersionMax) >= 0) return false;
        }

        Map<String, Boolean> safeFeatures = actualFeatures == null ? Collections.emptyMap() : actualFeatures;
        for (Map.Entry<String, Boolean> entry : features.entrySet()) {
            boolean actual = Boolean.TRUE.equals(safeFeatures.get(entry.getKey()));
            if (entry.getValue() != actual) return false;
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
