package com.mf.mylauncher.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Selects native archive classifiers from Mojang metadata for an explicit target.
 * Callers must know that the selected native binaries are compatible with their runtime/OS ABI.
 */
public final class NativeLibraryPlanner {
    private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9._+-]{1,32}");

    private NativeLibraryPlanner() { }

    public static NativeLibraryPlan create(VersionDetails details, LibraryPlatform rulePlatform,
                                           String nativeOsName, String architectureToken) {
        if (details == null) throw new IllegalArgumentException("Version details are required");
        if (rulePlatform == null) throw new IllegalArgumentException("Library-rule platform is required");
        validateToken(nativeOsName, "Native OS name");
        validateToken(architectureToken, "Native architecture token");

        // The JVM platform used for metadata rules and the requested native target must agree.
        // Otherwise an OS-neutral library rule could accidentally select another OS's native JAR.
        if (!sameOperatingSystem(rulePlatform.getOsName(), nativeOsName)) {
            return new NativeLibraryPlan(details.getId(), nativeOsName, architectureToken,
                    java.util.Collections.emptyList());
        }

        Map<String, NativeLibraryArchive> byPath = new LinkedHashMap<>();
        for (LibraryMetadata library : details.getLibraries()) {
            if (!library.isAllowedOn(rulePlatform.getOsName(), rulePlatform.getArchitecture(),
                    rulePlatform.getOsVersion(), rulePlatform.getFeatures())) continue;
            Optional<DownloadArtifact> selected;
            if (library.getNatives().containsKey(nativeOsName)) {
                selected = library.findNativeArtifact(nativeOsName, architectureToken);
                if (!selected.isPresent()) {
                    throw new IllegalArgumentException("Native classifier declared for " + library.getName()
                            + " on " + nativeOsName + " does not exist for architecture token " + architectureToken);
                }
            } else if (library.hasExplicitNativeClassifier()) {
                if (!library.isExplicitNativeClassifierCompatible(nativeOsName, architectureToken)) continue;
                selected = library.getArtifact();
                if (!selected.isPresent()) {
                    throw new IllegalArgumentException("Explicit native classifier has no artifact for " + library.getName());
                }
            } else {
                continue;
            }
            DownloadArtifact artifact = selected.get();
            if (!artifact.hasPath()) {
                throw new IllegalArgumentException("Native classifier has no safe library path for " + library.getName());
            }
            List<String> extractionExcludes = new ArrayList<>(library.getExtractionExcludes());
            if (!extractionExcludes.contains("META-INF/")) extractionExcludes.add("META-INF/");
            NativeLibraryArchive nativeArchive = new NativeLibraryArchive(
                    library.getName(), artifact, extractionExcludes);
            NativeLibraryArchive previous = byPath.putIfAbsent(nativeArchive.getRelativeLibraryPath(), nativeArchive);
            if (previous != null && !previous.hasSameArchive(nativeArchive)) {
                throw new IllegalArgumentException("Conflicting native archives target the same library path: "
                        + nativeArchive.getRelativeLibraryPath());
            }
        }
        return new NativeLibraryPlan(details.getId(), nativeOsName, architectureToken,
                new ArrayList<>(byPath.values()));
    }

    private static boolean sameOperatingSystem(String ruleOsName, String nativeOsName) {
        return ruleOsName.equals(nativeOsName)
                || ("osx".equals(ruleOsName) && "macos".equals(nativeOsName))
                || ("macos".equals(ruleOsName) && "osx".equals(nativeOsName));
    }

    private static void validateToken(String value, String label) {
        if (value == null || !SAFE_TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must be a safe metadata token");
        }
    }
}
