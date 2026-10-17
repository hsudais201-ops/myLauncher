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
