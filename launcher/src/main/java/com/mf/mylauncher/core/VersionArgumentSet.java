package com.mf.mylauncher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validated modern Minecraft game/JVM argument templates and their conditional rules. */
public final class VersionArgumentSet {
    private final List<ArgumentEntry> game;
    private final List<ArgumentEntry> jvm;
    private final List<ArgumentEntry> defaultUserJvm;

    public VersionArgumentSet(List<ArgumentEntry> game, List<ArgumentEntry> jvm) {
        this(game, jvm, Collections.emptyList());
    }

    /**
     * default-user-jvm contains user-facing JVM defaults (Minecraft 26.1+), not unconditional
     * launch flags. Callers must merge these suggestions with the user's settings and device-safe
     * memory limit rather than blindly passing Mojang desktop defaults to Android.
     */
    public VersionArgumentSet(List<ArgumentEntry> game, List<ArgumentEntry> jvm,
                              List<ArgumentEntry> defaultUserJvm) {
        this.game = immutableCopy(game, "game arguments");
        this.jvm = immutableCopy(jvm, "JVM arguments");
        this.defaultUserJvm = immutableCopy(defaultUserJvm, "default user JVM arguments");
    }

    public static VersionArgumentSet empty() {
        return new VersionArgumentSet(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public static VersionArgumentSet fromDecodedJson(Map<?, ?> root) {
        if (root == null) throw new IllegalArgumentException("arguments must be a JSON object");
        return new VersionArgumentSet(
                parseEntries(root.get("game"), "arguments.game"),
                parseEntries(root.get("jvm"), "arguments.jvm"),
                parseEntries(root.get("default-user-jvm"), "arguments.default-user-jvm"));
    }

    public List<ArgumentEntry> getGame() { return game; }
    public List<ArgumentEntry> getJvm() { return jvm; }
    public List<ArgumentEntry> getDefaultUserJvm() { return defaultUserJvm; }

    public List<String> resolveGameArguments(LibraryPlatform platform) { return resolve(game, platform); }
    public List<String> resolveJvmArguments(LibraryPlatform platform) { return resolve(jvm, platform); }

    /** Resolves default JVM suggestions for the settings UI; does not apply them to a launch. */
    public List<String> resolveDefaultUserJvmArguments(LibraryPlatform platform) {
        return resolve(defaultUserJvm, platform);
    }

    private static List<String> resolve(List<ArgumentEntry> entries, LibraryPlatform platform) {
        if (platform == null) throw new IllegalArgumentException("Launch platform is required");
        List<String> result = new ArrayList<>();
        for (ArgumentEntry entry : entries) if (entry.isAllowed(platform)) result.addAll(entry.getValues());
        return Collections.unmodifiableList(result);
    }

    private static List<ArgumentEntry> parseEntries(Object value, String field) {
        if (value == null) return Collections.emptyList();
        if (!(value instanceof List<?>)) throw new IllegalArgumentException(field + " must be a JSON array");
        List<ArgumentEntry> entries = new ArrayList<>();
        int index = 0;
        for (Object raw : (List<?>) value) {
            String at = field + "[" + index + "]";
            if (raw instanceof String) {
                entries.add(new ArgumentEntry(Collections.singletonList((String) raw), Collections.emptyList()));
            } else if (raw instanceof Map<?, ?>) {
                Map<?, ?> object = (Map<?, ?>) raw;
                Object rawValues = object.get("value");
                List<String> values = new ArrayList<>();
                if (rawValues instanceof String) values.add((String) rawValues);
                else if (rawValues instanceof List<?>) {
                    for (Object item : (List<?>) rawValues) {
                        if (!(item instanceof String)) throw new IllegalArgumentException(at + ".value array must contain only strings");
                        values.add((String) item);
                    }
                } else throw new IllegalArgumentException(at + ".value must be a string or string array");

                Object rawRules = object.get("rules");
                List<LibraryRule> rules = new ArrayList<>();
                if (rawRules != null) {
                    if (!(rawRules instanceof List<?>)) throw new IllegalArgumentException(at + ".rules must be a JSON array");
                    int ruleIndex = 0;
                    for (Object rawRule : (List<?>) rawRules) {
                        if (!(rawRule instanceof Map<?, ?>)) throw new IllegalArgumentException(at + ".rules entries must be objects");
                        rules.add(parseRule((Map<?, ?>) rawRule, at + ".rules[" + ruleIndex + "]"));
                        ruleIndex++;
                    }
                }
                entries.add(new ArgumentEntry(values, rules));
            } else throw new IllegalArgumentException(at + " must be a string or argument object");
            index++;
        }
        return entries;
    }

    private static LibraryRule parseRule(Map<?, ?> value, String field) {
        Object rawAction = value.get("action");
        if (!(rawAction instanceof String)
                || (!"allow".equals(rawAction) && !"disallow".equals(rawAction))) {
            throw new IllegalArgumentException(field + ".action must be allow or disallow");
        }
        String osName = null, osArch = null, osVersion = null, osVersionMin = null, osVersionMax = null;
        Object rawOs = value.get("os");
        if (rawOs != null) {
            if (!(rawOs instanceof Map<?, ?>)) throw new IllegalArgumentException(field + ".os must be an object");
            Map<?, ?> os = (Map<?, ?>) rawOs;
            osName = optionalString(os.get("name"), field + ".os.name");
            osArch = optionalString(os.get("arch"), field + ".os.arch");
            osVersion = optionalString(os.get("version"), field + ".os.version");
            Object rawVersionRange = os.get("versionRange");
            if (rawVersionRange != null) {
                if (!(rawVersionRange instanceof Map<?, ?>)) {
                    throw new IllegalArgumentException(field + ".os.versionRange must be an object");
                }
                Map<?, ?> versionRange = (Map<?, ?>) rawVersionRange;
                osVersionMin = optionalString(versionRange.get("min"), field + ".os.versionRange.min");
                osVersionMax = optionalString(versionRange.get("max"), field + ".os.versionRange.max");
                if (osVersionMin == null && osVersionMax == null) {
                    throw new IllegalArgumentException(field + ".os.versionRange must contain min or max");
                }
            }
        }
        Map<String, Boolean> features = new LinkedHashMap<>();
        Object rawFeatures = value.get("features");
        if (rawFeatures != null) {
            if (!(rawFeatures instanceof Map<?, ?>)) throw new IllegalArgumentException(field + ".features must be an object");
            for (Map.Entry<?, ?> item : ((Map<?, ?>) rawFeatures).entrySet()) {
                if (!(item.getKey() instanceof String) || !(item.getValue() instanceof Boolean)) {
                    throw new IllegalArgumentException(field + ".features must map names to booleans");
                }
                features.put((String) item.getKey(), (Boolean) item.getValue());
            }
        }
        return new LibraryRule("allow".equals(rawAction), osName, osArch, osVersion,
                osVersionMin, osVersionMax, features);
    }

    private static String optionalString(Object value, String field) {
        if (value == null) return null;
        if (!(value instanceof String)) throw new IllegalArgumentException(field + " must be a string");
        return (String) value;
    }

    private static List<ArgumentEntry> immutableCopy(List<ArgumentEntry> source, String label) {
        if (source == null) throw new IllegalArgumentException(label + " are required");
        List<ArgumentEntry> copy = new ArrayList<>();
        for (ArgumentEntry entry : source) {
            if (entry == null) throw new IllegalArgumentException(label + " must not contain null entries");
            copy.add(entry);
        }
        return Collections.unmodifiableList(copy);
    }

    public static final class ArgumentEntry {
        private final List<String> values;
        private final List<LibraryRule> rules;

        public ArgumentEntry(List<String> values, List<LibraryRule> rules) {
            if (values == null || values.isEmpty()) throw new IllegalArgumentException("Argument value list must not be empty");
            List<String> copy = new ArrayList<>();
            for (String value : values) {
                if (value == null || value.isEmpty()) throw new IllegalArgumentException("Argument values must not be empty");
                copy.add(value);
            }
            this.values = Collections.unmodifiableList(copy);
            if (rules == null) throw new IllegalArgumentException("Argument rules are required; use an empty list for unconditional values");
            List<LibraryRule> ruleCopy = new ArrayList<>();
            for (LibraryRule rule : rules) {
                if (rule == null) throw new IllegalArgumentException("Argument rules must not contain null entries");
                ruleCopy.add(rule);
            }
            this.rules = Collections.unmodifiableList(ruleCopy);
        }

        public List<String> getValues() { return values; }
        public List<LibraryRule> getRules() { return rules; }
        public boolean isAllowed(LibraryPlatform platform) {
            return LibraryRule.isAllowed(rules, platform.getOsName(), platform.getArchitecture(),
                    platform.getOsVersion(), platform.getFeatures());
        }
    }
}
