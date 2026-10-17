package com.mf.mylauncher.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Parsed metadata for one client library and any platform-specific native classifiers. */
public final class LibraryMetadata {
    private static final Pattern SAFE_COORDINATE = Pattern.compile("[^\\s:]+:[^\\s:]+:[^\\s:]+(?::[^\\s:]+)?(?:@[^\\s:]+)?");
    private final String name;
    private final DownloadArtifact artifact;
    private final Map<String, DownloadArtifact> classifiers;
    private final Map<String, String> natives;
    private final List<LibraryRule> rules;
    private final List<String> extractionExcludes;

    public LibraryMetadata(String name, DownloadArtifact artifact,
                           Map<String, DownloadArtifact> classifiers, Map<String, String> natives,
                           List<LibraryRule> rules, List<String> extractionExcludes) {
        if (name == null || name.length() > 512 || !SAFE_COORDINATE.matcher(name).matches()) {
            throw new IllegalArgumentException("Library Maven coordinate is invalid");
        }
        this.name = name;
        this.artifact = artifact;
        this.classifiers = immutableMap(classifiers, "classifiers");
        this.natives = immutableMap(natives, "native classifiers");
        this.rules = immutableList(rules, "rules");
        List<String> excludes = new ArrayList<>();
        if (extractionExcludes != null) {
            for (String path : extractionExcludes) {
                if (path == null || path.isEmpty() || path.startsWith("/") || path.contains("\\")) {
                    throw new IllegalArgumentException("Native extraction exclusion path is unsafe");
                }
                for (String segment : path.split("/")) {
                    if (segment.equals(".") || segment.equals("..")) {
                        throw new IllegalArgumentException("Native extraction exclusion path must not traverse directories");
                    }
                }
                excludes.add(path);
            }
        }
        this.extractionExcludes = Collections.unmodifiableList(excludes);
        if (artifact == null && this.classifiers.isEmpty()) {
            throw new IllegalArgumentException("A library must declare an artifact or at least one classifier");
        }
        for (Map.Entry<String, String> entry : this.natives.entrySet()) {
            if (entry.getKey().isEmpty() || entry.getValue().isEmpty()) {
                throw new IllegalArgumentException("Native classifier names must not be empty");
            }
        }
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> source, String label) {
        Map<String, T> copy = new LinkedHashMap<>();
        if (source != null) {
            for (Map.Entry<String, T> entry : source.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isEmpty() || entry.getValue() == null) {
                    throw new IllegalArgumentException(label + " contain an empty key or null value");
                }
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static <T> List<T> immutableList(List<T> source, String label) {
        List<T> copy = new ArrayList<>();
        if (source != null) {
            for (T item : source) {
                if (item == null) throw new IllegalArgumentException(label + " must not contain null entries");
                copy.add(item);
            }
        }
        return Collections.unmodifiableList(copy);
    }

    public String getName() { return name; }
    public Optional<DownloadArtifact> getArtifact() { return Optional.ofNullable(artifact); }

    /** Returns the explicit Maven classifier, when the coordinate has four colon-separated parts. */
    public Optional<String> getExplicitClassifier() {
        String[] parts = name.split(":", -1);
        if (parts.length != 4) return Optional.empty();
        String classifier = parts[3];
        int extension = classifier.indexOf('@');
        if (extension >= 0) classifier = classifier.substring(0, extension);
        return classifier.isEmpty() ? Optional.empty() : Optional.of(classifier);
    }

    /**
     * Newer Minecraft metadata lists platform native archives as ordinary artifacts with an
     * explicit classifier instead of the legacy natives/classifiers maps.
     */
    public boolean hasExplicitNativeClassifier() {
        return getExplicitClassifier().map(classifier -> classifier.startsWith("natives-")).orElse(false);
    }

    /** True for known OS/CPU-qualified classifiers, including non-native jars with embedded native resources. */
    public boolean hasExplicitPlatformClassifier() {
        Optional<String> optional = getExplicitClassifier();
        if (!optional.isPresent()) return false;
        String classifier = optional.get();
        return hasExplicitNativeClassifier()
                || classifier.matches("(?:linux|windows|osx|macos)-(?:aarch_64|x86_64|arm64(?:-v8a)?|arm32(?:-v7a|hf|v6|v7)?|x86|i386|i486|i586|i686)");
    }

    /** Selects only native-archive classifiers that target the requested OS and CPU architecture. */
    public boolean isExplicitNativeClassifierCompatible(String osName, String architecture) {
        if (!hasExplicitNativeClassifier()) return false;
        return matchesClassifierTarget(getExplicitClassifier().get(), osName, architecture);
    }

    /** Filters platform-specific artifacts from downloads/classpath when they target another CPU. */
    public boolean isExplicitPlatformClassifierCompatible(String osName, String architecture) {
        if (!hasExplicitPlatformClassifier()) return true;
        return matchesClassifierTarget(getExplicitClassifier().get(), osName, architecture);
    }

    private static boolean matchesClassifierTarget(String value, String osName, String architecture) {
        String classifier = value.startsWith("natives-") ? value.substring("natives-".length()) : value;
        String[] parts = classifier.split("-", -1);
        if (parts.length == 0) return false;
        String classifierOs = normalizeClassifierOs(parts[0]);
        if (classifierOs == null || !classifierOs.equals(normalizeClassifierOs(osName))) return false;

        String classifierArch;
        if (parts.length == 1) {
            // Mojang's unqualified natives-linux/macos/windows artifacts are the desktop x86_64 variant.
            classifierArch = "x86_64";
        } else {
            String suffix = classifier.substring(parts[0].length() + 1);
            classifierArch = normalizeClassifierArch(suffix);
        }
        return classifierArch != null && classifierArch.equals(normalizeClassifierArch(architecture));
    }

    /** Platform-specific artifacts must not leak into the classpath for another CPU target. */
    public boolean isCompatibleWithPlatform(LibraryPlatform platform) {
        if (platform == null) throw new IllegalArgumentException("Library platform is required");
        return isExplicitPlatformClassifierCompatible(platform.getOsName(), platform.getArchitecture());
    }

    private static String normalizeClassifierOs(String value) {
        if ("macos".equals(value)) return "osx";
        return ("linux".equals(value) || "osx".equals(value) || "windows".equals(value)) ? value : null;
    }

    private static String normalizeClassifierArch(String value) {
        if ("arm64".equals(value) || "aarch64".equals(value) || "aarch_64".equals(value)
                || "arm64-v8a".equals(value)) return "arm64";
        if ("x86_64".equals(value) || "amd64".equals(value) || "x64".equals(value)) return "x86_64";
        if ("x86".equals(value) || "i386".equals(value) || "i486".equals(value)
                || "i586".equals(value) || "i686".equals(value)) return "x86";
        if ("arm".equals(value) || "arm32".equals(value) || "armhf".equals(value)
                || "arm32hf".equals(value) || "arm32v6".equals(value) || "arm32v7".equals(value)
                || "arm32-v7a".equals(value)) return "arm32";
        return null;
    }
    public Map<String, DownloadArtifact> getClassifiers() { return classifiers; }
    public Map<String, String> getNatives() { return natives; }
    public List<LibraryRule> getRules() { return rules; }
    public List<String> getExtractionExcludes() { return extractionExcludes; }

    public boolean isAllowedOn(String osName, String osArch, String osVersion,
                               Map<String, Boolean> features) {
        return LibraryRule.isAllowed(rules, osName, osArch, osVersion, features);
    }

    public Optional<DownloadArtifact> findNativeArtifact(String osName, String architectureToken) {
        String classifierTemplate = natives.get(osName);
        if (classifierTemplate == null) return Optional.empty();
        String classifier = classifierTemplate.replace("${arch}", architectureToken == null ? "" : architectureToken);
        return Optional.ofNullable(classifiers.get(classifier));
    }
}
