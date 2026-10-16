package com.mf.mylauncher.core;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maps a decoded Mojang per-version JSON document into validated download/launch metadata. */
public final class VersionDetailsMapper {
    private VersionDetailsMapper() { }

    public static VersionDetails fromDecodedJson(Map<String, ?> root) {
        return fromDecodedJson(root, null);
    }

    public static VersionDetails fromDecodedJson(Map<String, ?> root, String expectedVersionId) {
        if (root == null) throw new IllegalArgumentException("Version metadata root must be a JSON object");
        String id = requiredString(root.get("id"), "id");
        if (expectedVersionId != null && !expectedVersionId.equals(id)) {
            throw new IllegalArgumentException("Requested version ID does not match metadata ID");
        }
        String mainClass = requiredString(root.get("mainClass"), "mainClass");
        String assetsId = requiredString(root.get("assets"), "assets");
        AssetIndexMetadata assetIndex = parseAssetIndex(requiredMap(root.get("assetIndex"), "assetIndex"));
        Map<?, ?> downloads = requiredMap(root.get("downloads"), "downloads");
        DownloadArtifact client = parseArtifact(requiredMap(downloads.get("client"), "downloads.client"),
                "downloads.client", false);

        int javaMajor = 8;
        Object rawJavaVersion = root.get("javaVersion");
        if (rawJavaVersion != null) {
            Map<?, ?> javaVersion = requiredMap(rawJavaVersion, "javaVersion");
            long parsedMajor = requiredLong(javaVersion.get("majorVersion"), "javaVersion.majorVersion");
            if (parsedMajor < 8 || parsedMajor > 99) {
                throw new IllegalArgumentException("javaVersion.majorVersion is outside the supported range");
            }
            javaMajor = (int) parsedMajor;
        }

        Object rawLibraries = root.get("libraries");
        if (!(rawLibraries instanceof List<?>)) {
            throw new IllegalArgumentException("libraries must be a JSON array");
        }
        List<LibraryMetadata> libraries = new ArrayList<>();
        int index = 0;
        for (Object rawLibrary : (List<?>) rawLibraries) {
            String field = "libraries[" + index + "]";
            try {
                libraries.add(parseLibrary(requiredMap(rawLibrary, field), field));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid library metadata at " + field + ": " + exception.getMessage(), exception);
            }
            index++;
        }
        VersionArgumentSet arguments = VersionArgumentSet.empty();
        Object rawArguments = root.get("arguments");
        if (rawArguments != null) {
            arguments = VersionArgumentSet.fromDecodedJson(requiredMap(rawArguments, "arguments"));
        }
        String legacyArguments = optionalString(root.get("minecraftArguments"), "minecraftArguments");
        return new VersionDetails(id, mainClass, assetsId, assetIndex, client, javaMajor, libraries,
                arguments, legacyArguments);
    }

    private static AssetIndexMetadata parseAssetIndex(Map<?, ?> value) {
        return new AssetIndexMetadata(
                requiredString(value.get("id"), "assetIndex.id"),
                parseUri(requiredString(value.get("url"), "assetIndex.url"), "assetIndex.url"),
                requiredString(value.get("sha1"), "assetIndex.sha1"),
                requiredLong(value.get("size"), "assetIndex.size"),
                optionalLong(value.get("totalSize"), 0L, "assetIndex.totalSize"));
    }

    private static LibraryMetadata parseLibrary(Map<?, ?> value, String field) {
        String name = requiredString(value.get("name"), field + ".name");
        DownloadArtifact artifact = null;
        Map<String, DownloadArtifact> classifiers = new LinkedHashMap<>();
        Object rawDownloads = value.get("downloads");
        if (rawDownloads != null) {
            Map<?, ?> downloads = requiredMap(rawDownloads, field + ".downloads");
            Object rawArtifact = downloads.get("artifact");
            if (rawArtifact != null) {
                artifact = parseArtifact(requiredMap(rawArtifact, field + ".downloads.artifact"),
                        field + ".downloads.artifact", true);
            }
            Object rawClassifiers = downloads.get("classifiers");
            if (rawClassifiers != null) {
                Map<?, ?> map = requiredMap(rawClassifiers, field + ".downloads.classifiers");
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String) || ((String) entry.getKey()).isEmpty()) {
                        throw new IllegalArgumentException(field + ".downloads.classifiers contains an invalid key");
                    }
                    classifiers.put((String) entry.getKey(), parseArtifact(
                            requiredMap(entry.getValue(), field + ".downloads.classifiers." + entry.getKey()),
                            field + ".downloads.classifiers." + entry.getKey(), true));
                }
            }
        }

        Map<String, String> natives = new LinkedHashMap<>();
        Object rawNatives = value.get("natives");
        if (rawNatives != null) {
            Map<?, ?> map = requiredMap(rawNatives, field + ".natives");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
                    throw new IllegalArgumentException(field + ".natives values must be strings");
                }
                natives.put((String) entry.getKey(), (String) entry.getValue());
            }
        }

        List<LibraryRule> rules = new ArrayList<>();
        Object rawRules = value.get("rules");
        if (rawRules != null) {
            if (!(rawRules instanceof List<?>)) throw new IllegalArgumentException(field + ".rules must be an array");
            for (Object rawRule : (List<?>) rawRules) rules.add(parseRule(requiredMap(rawRule, field + ".rules"), field + ".rules"));
        }

        List<String> excludes = new ArrayList<>();
        Object rawExtract = value.get("extract");
        if (rawExtract != null) {
            Map<?, ?> extract = requiredMap(rawExtract, field + ".extract");
            Object rawExcludes = extract.get("exclude");
            if (rawExcludes != null) {
                if (!(rawExcludes instanceof List<?>)) throw new IllegalArgumentException(field + ".extract.exclude must be an array");
                for (Object rawPath : (List<?>) rawExcludes) {
                    if (!(rawPath instanceof String)) throw new IllegalArgumentException(field + ".extract.exclude entries must be strings");
                    excludes.add((String) rawPath);
                }
            }
        }
        return new LibraryMetadata(name, artifact, classifiers, natives, rules, excludes);
    }

    private static LibraryRule parseRule(Map<?, ?> value, String field) {
        String action = requiredString(value.get("action"), field + ".action");
        if (!"allow".equals(action) && !"disallow".equals(action)) {
            throw new IllegalArgumentException(field + ".action must be allow or disallow");
        }
        String osName = null, osArch = null, osVersion = null;
        Object rawOs = value.get("os");
        if (rawOs != null) {
            Map<?, ?> os = requiredMap(rawOs, field + ".os");
            osName = optionalString(os.get("name"), field + ".os.name");
            osArch = optionalString(os.get("arch"), field + ".os.arch");
            osVersion = optionalString(os.get("version"), field + ".os.version");
        }
        Map<String, Boolean> features = new LinkedHashMap<>();
        Object rawFeatures = value.get("features");
        if (rawFeatures != null) {
            Map<?, ?> map = requiredMap(rawFeatures, field + ".features");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Boolean)) {
                    throw new IllegalArgumentException(field + ".features must map names to boolean values");
                }
                features.put((String) entry.getKey(), (Boolean) entry.getValue());
            }
        }
        return new LibraryRule("allow".equals(action), osName, osArch, osVersion, features);
    }

    private static DownloadArtifact parseArtifact(Map<?, ?> value, String field, boolean requirePath) {
        String path = optionalString(value.get("path"), field + ".path");
        if (requirePath && path == null) throw new IllegalArgumentException(field + ".path is required");
        return new DownloadArtifact(
                parseUri(requiredString(value.get("url"), field + ".url"), field + ".url"),
                requiredString(value.get("sha1"), field + ".sha1"),
                requiredLong(value.get("size"), field + ".size"), path);
    }

    private static URI parseUri(String value, String field) {
        try { return URI.create(value); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException(field + " must be a valid URL", exception); }
    }

    private static Map<?, ?> requiredMap(Object value, String field) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException(field + " must be a JSON object");
        return (Map<?, ?>) value;
    }

    private static String requiredString(Object value, String field) {
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must be a non-empty string");
        }
        return (String) value;
    }

    private static String optionalString(Object value, String field) {
        if (value == null) return null;
        if (!(value instanceof String)) throw new IllegalArgumentException(field + " must be a string");
        return (String) value;
    }

    private static long requiredLong(Object value, String field) {
        if (!(value instanceof Number)) throw new IllegalArgumentException(field + " must be a number");
        try {
            if (value instanceof BigDecimal) return ((BigDecimal) value).longValueExact();
            long result = ((Number) value).longValue();
            if (Double.isFinite(((Number) value).doubleValue()) && ((Number) value).doubleValue() == result) return result;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(field + " must be an integer in range", exception);
        }
        throw new IllegalArgumentException(field + " must be an integer in range");
    }

    private static long optionalLong(Object value, long defaultValue, String field) {
        return value == null ? defaultValue : requiredLong(value, field);
    }
}
