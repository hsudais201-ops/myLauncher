package com.mf.mylauncher.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dependency-free regression tests so the launcher core can be tested with only a JDK. */
public final class CoreTests {
    private static int checks;
    private CoreTests() { }

    public static void main(String[] args) {
        versions(); catalogs(); manifests(); jsonDecoder(); versionDetails(); downloader(); profiles(); memory();
        System.out.println("PASS: " + checks + " launcher-core assertions");
    }

    private static void versions() {
        MinecraftVersion v = version("1.21.4", "release", "2024-12-03T10:00:00Z");
        eq("1.21.4", v.getId(), "ID retained"); yes(v.isRelease(), "release recognized");
        eq("https", v.getMetadataUrl().getScheme(), "HTTPS retained");
        bad(() -> version("../outside", "release", time1), "path-like ID rejected");
        bad(() -> version("1.21/4", "release", time1), "slash in ID rejected");
        bad(() -> new MinecraftVersion("1.21.4", "release", URI.create("http://example.test/v.json"), time1), "HTTP metadata URL rejected");
        bad(() -> version("1.21.4", "release", "bad timestamp"), "bad timestamp rejected");
        bad(() -> new MinecraftVersion("1.21.4", "release", URI.create("https://user@example.test/v.json"), time1), "URL userinfo rejected");
    }
    private static final String time1 = "2024-12-03T10:00:00Z";

    private static void catalogs() {
        MinecraftVersion old = version("1.20.6", "release", "2024-04-29T10:00:00Z");
        MinecraftVersion snap = version("25w01a", "snapshot", "2025-01-01T10:00:00Z");
        MinecraftVersion latest = version("1.21.4", "release", time1);
        VersionCatalog c = new VersionCatalog(Arrays.asList(old, snap, latest));
        eq(3, c.size(), "catalog size"); eq("25w01a", c.getVersionsNewestFirst().get(0).getId(), "newest-first sort");
        eq("1.21.4", c.getLatestRelease().get().getId(), "latest release skips newer snapshot");
        eq("25w01a", c.getLatestSnapshot().get().getId(), "latest snapshot selected");
        eq(latest, c.findById("1.21.4").get(), "lookup by ID");
        yes(!c.findById("missing").isPresent() && !c.findById(null).isPresent(), "unknown/null lookup is empty");
        eq(2, c.getReleasesNewestFirst().size(), "release filter");
        bad(() -> new VersionCatalog(null), "null list rejected");
        bad(() -> new VersionCatalog(Arrays.asList(latest, null)), "null entry rejected");
        bad(() -> new VersionCatalog(Arrays.asList(latest, version("1.21.4", "snapshot", "2025-01-01T00:00:00Z"))), "duplicate IDs rejected");
        unsupported(() -> c.getVersionsNewestFirst().clear(), "catalog list immutable");
    }

    @SuppressWarnings("unchecked")
    private static void manifests() {
        VersionManifest m = VersionManifestMapper.fromDecodedJson(manifestObject());
        eq("1.21.4", m.getLatestRelease().getId(), "latest release mapped");
        eq("25w01a", m.getLatestSnapshot().getId(), "latest snapshot mapped");
        eq(2, m.getCatalog().size(), "versions mapped");
        bad(() -> VersionManifestMapper.fromDecodedJson(null), "null root rejected");
        Map<String,Object> noLatest = manifestObject(); noLatest.remove("latest");
        bad(() -> VersionManifestMapper.fromDecodedJson(noLatest), "missing latest rejected");
        Map<String,Object> wrongArray = manifestObject(); wrongArray.put("versions", "bad");
        bad(() -> VersionManifestMapper.fromDecodedJson(wrongArray), "non-array versions rejected");
        Map<String,Object> wrongLatest = manifestObject();
        ((Map<String,Object>) wrongLatest.get("latest")).put("release", "25w01a");
        bad(() -> VersionManifestMapper.fromDecodedJson(wrongLatest), "release cannot point to snapshot");
        Map<String,Object> unknown = manifestObject();
        ((Map<String,Object>) unknown.get("latest")).put("release", "9.99");
        bad(() -> VersionManifestMapper.fromDecodedJson(unknown), "latest ID must exist");
        Map<String,Object> unsafe = manifestObject();
        ((List<Map<String,Object>>) unsafe.get("versions")).get(0).put("url", "http://example.test/v.json");
        bad(() -> VersionManifestMapper.fromDecodedJson(unsafe), "insecure metadata URL rejected");
    }

    private static void jsonDecoder() {
        String json = "{\"latest\":{\"release\":\"1.21.4\",\"snapshot\":\"25w01a\"},"
                + "\"versions\":[{\"id\":\"1.21.4\",\"type\":\"release\","
                + "\"url\":\"https://piston-meta.mojang.com/versions/1.21.4.json\","
                + "\"releaseTime\":\"2024-12-03T10:00:00Z\",\"complianceLevel\":1},"
                + "{\"id\":\"25w01a\",\"type\":\"snapshot\","
                + "\"url\":\"https://piston-meta.mojang.com/versions/25w01a.json\","
                + "\"releaseTime\":\"2025-01-01T10:00:00Z\"}]}";
        VersionManifest manifest = VersionManifestJsonDecoder.decode(json);
        eq("1.21.4", manifest.getLatestRelease().getId(), "JSON text decodes to release version");
        eq("25w01a", manifest.getLatestSnapshot().getId(), "JSON text decodes to snapshot version");
        eq(2, manifest.getCatalog().size(), "JSON versions array integrated end-to-end");
        Map<?, ?> primitives = (Map<?, ?>) JsonParser.parse("{\"text\":\"line\\nquote:\\u0022\",\"number\":-1.25e2,\"yes\":true,\"nothing\":null}");
        eq("line\nquote:\"", primitives.get("text"), "JSON escapes decoded");
        eq(new java.math.BigDecimal("-1.25e2"), primitives.get("number"), "JSON exponent number parsed");
        eq(Boolean.TRUE, primitives.get("yes"), "JSON boolean parsed");
        yes(primitives.containsKey("nothing") && primitives.get("nothing") == null, "JSON null retained");
        bad(() -> VersionManifestJsonDecoder.decode("[]"), "array root rejected");
        bad(() -> VersionManifestJsonDecoder.decode("{\"latest\":null}"), "missing manifest values rejected");
        bad(() -> JsonParser.parse("{\"duplicate\":1,\"duplicate\":2}"), "duplicate JSON keys rejected");
        bad(() -> JsonParser.parse("{\"bad\":01}"), "leading zero rejected");
        bad(() -> JsonParser.parse("{\"bad\":\"\\x\"}"), "invalid string escape rejected");
        bad(() -> JsonParser.parse("{\"bad\":1,}"), "trailing comma rejected");
        bad(() -> JsonParser.parse("true false"), "trailing document data rejected");
        bad(() -> JsonParser.parse("\"\\uＦＦＦＦ\""), "non-ASCII Unicode escape digits rejected");
        bad(() -> JsonParser.parse("\"\\uD800\""), "unpaired Unicode surrogate rejected");
        bad(() -> JsonParser.parse("[" + repeat("[", 66) + "0" + repeat("]", 66) + "]"), "extreme JSON nesting rejected");
        bad(() -> JsonParser.parse("\"" + repeat("x", 8 * 1024 * 1024) + "\""), "oversized JSON text rejected");
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static void versionDetails() {
        String sha1 = "0123456789abcdef0123456789abcdef01234567";
        String sha2 = "fedcba9876543210fedcba9876543210fedcba98";
        String json = "{\"id\":\"1.21.4\",\"mainClass\":\"net.minecraft.client.main.Main\","
                + "\"assets\":\"1.21.4\",\"assetIndex\":{\"id\":\"1.21.4\","
                + "\"url\":\"https://piston-meta.mojang.com/v1/packages/" + sha1 + "/1.21.4.json\","
                + "\"sha1\":\"" + sha1 + "\",\"size\":88,\"totalSize\":424},"
                + "\"downloads\":{\"client\":{\"url\":\"https://piston-data.mojang.com/v1/objects/" + sha2 + "/client.jar\","
                + "\"sha1\":\"" + sha2 + "\",\"size\":9000000}},\"javaVersion\":{\"majorVersion\":21},"
                + "\"libraries\":[{\"name\":\"org.example:core:1.0\",\"downloads\":{\"artifact\":{"
                + "\"path\":\"org/example/core/1.0/core-1.0.jar\",\"url\":\"https://libraries.minecraft.net/core.jar\","
                + "\"sha1\":\"" + sha1 + "\",\"size\":123}},\"rules\":["
                + "{\"action\":\"allow\",\"os\":{\"name\":\"linux\",\"arch\":\"aarch64\",\"version\":\"6\\\\..*\"}},"
                + "{\"action\":\"disallow\",\"features\":{\"is_demo_user\":true}}]},"
                + "{\"name\":\"org.lwjgl:lwjgl:3.3.3\",\"downloads\":{\"classifiers\":{"
                + "\"natives-linux-arm64\":{\"path\":\"org/lwjgl/lwjgl/3.3.3/lwjgl-natives.jar\","
                + "\"url\":\"https://libraries.minecraft.net/lwjgl-natives.jar\",\"sha1\":\"" + sha2 + "\",\"size\":456}}},"
                + "\"natives\":{\"linux\":\"natives-linux-${arch}\"},\"extract\":{\"exclude\":[\"META-INF/\"]}}]}";
        VersionDetails details = VersionDetailsJsonDecoder.decode(json, "1.21.4");
        eq("1.21.4", details.getId(), "per-version ID mapped");
        eq("net.minecraft.client.main.Main", details.getMainClass(), "main class mapped");
        eq(21, details.getJavaMajorVersion(), "required Java major version mapped");
        eq("1.21.4", details.getAssetIndex().getId(), "asset-index ID mapped");
        eq(424L, details.getAssetIndex().getTotalSizeBytes(), "asset-index total size mapped");
        eq(9000000L, details.getClientDownload().getSizeBytes(), "client artifact size mapped");
        yes(!details.getClientDownload().hasPath(), "client JAR destination is launcher-selected");
        eq(2, details.getLibraries().size(), "library list mapped");
        LibraryMetadata core = details.getLibraries().get(0);
        eq("org/example/core/1.0/core-1.0.jar", core.getArtifact().get().getPath(), "library path mapped");
        yes(core.isAllowedOn("linux", "aarch64", "6.1.0", java.util.Collections.singletonMap("is_demo_user", false)), "matching allow rule accepted");
        yes(!core.isAllowedOn("linux", "aarch64", "6.1.0", java.util.Collections.singletonMap("is_demo_user", true)), "later matching disallow rule overrides allow");
        yes(!core.isAllowedOn("windows", "aarch64", "6.1.0", java.util.Collections.emptyMap()), "non-matching rule set denies library");
        LibraryMetadata lwjgl = details.getLibraries().get(1);
        eq("0123456789abcdef0123456789abcdef01234567", core.getArtifact().get().getSha1(), "artifact hash normalized");
        eq("natives-linux-arm64", lwjgl.findNativeArtifact("linux", "arm64").map(a -> "natives-linux-arm64").orElse("missing"), "native classifier resolved");
        eq(1, lwjgl.getExtractionExcludes().size(), "native extraction exclusions mapped");
        eq(8, VersionDetailsJsonDecoder.decode(json.replace("\"javaVersion\":{\"majorVersion\":21},", "")).getJavaMajorVersion(), "legacy metadata defaults Java major to 8");
        bad(() -> VersionDetailsJsonDecoder.decode(json, "1.20.6"), "requested version mismatch rejected");
        String badHash = json.replace(sha1, "invalid");
        bad(() -> VersionDetailsJsonDecoder.decode(badHash), "malformed artifact hashes rejected");
        bad(() -> VersionDetailsJsonDecoder.decode(json.replace("org/example/core/1.0/core-1.0.jar", "../escape.jar")), "artifact path traversal rejected");
        bad(() -> VersionDetailsJsonDecoder.decode(json.replace("6\\\\..*", "[broken")), "invalid rule regex rejected");
        bad(() -> VersionDetailsJsonDecoder.decode(json.replace("\"majorVersion\":21", "\"majorVersion\":4294967317")), "overflowed Java major version rejected");
        unsupported(() -> details.getLibraries().clear(), "version libraries immutable");
        yes(LibraryRule.isAllowed(java.util.Collections.emptyList(), "linux", "arm64", "6.1", null), "libraries with no rules are allowed");

        URI metadataUrl = URI.create("https://piston-meta.mojang.com/v1/packages/" + sha1 + "/1.21.4.json");
        MinecraftVersion manifestEntry = new MinecraftVersion("1.21.4", "release", metadataUrl, time1);
        Fake response = new Fake(200, json.getBytes(StandardCharsets.UTF_8), -1);
        VersionDetailsDownloader detailsDownloader = new VersionDetailsDownloader(new VersionManifestDownloader(uri -> {
            eq(metadataUrl, uri, "version-details downloader uses manifest URL");
            return response;
        }));
        try {
            eq("1.21.4", detailsDownloader.download(manifestEntry).getId(), "per-version HTTP metadata decoded end-to-end");
        } catch (IOException exception) { throw new AssertionError(exception); }
        yes(response.disconnected && !response.followRedirects, "version metadata request is bounded and connection is closed");
        yes(VersionManifestDownloader.isApprovedMetadataUri(metadataUrl), "official piston metadata URL allowed");
        yes(VersionManifestDownloader.isApprovedMetadataUri(URI.create("https://launchermeta.mojang.com/v1/packages/abc/1.12.2.json")), "legacy official metadata host allowed");
        yes(!VersionManifestDownloader.isApprovedMetadataUri(URI.create("http://piston-meta.mojang.com/v1/packages/abc/1.21.4.json")), "HTTP metadata URL blocked");
        yes(!VersionManifestDownloader.isApprovedMetadataUri(URI.create("https://piston-meta.mojang.com.evil.test/v1/packages/abc/1.21.4.json")), "lookalike host blocked");
        yes(!VersionManifestDownloader.isApprovedMetadataUri(URI.create("https://piston-meta.mojang.com:444/v1/packages/abc/1.21.4.json")), "unexpected TLS port blocked");
        MinecraftVersion untrusted = new MinecraftVersion("1.21.4", "release", URI.create("https://evil.test/v1/packages/abc/1.21.4.json"), time1);
        io(() -> new VersionDetailsDownloader(new VersionManifestDownloader(uri -> { throw new AssertionError("unapproved URL reached connection opener"); })).download(untrusted), "unapproved metadata host blocked before network access");
    }

    private static void downloader() {
        String body = "{\"latest\":{\"release\":\"1.21.4\",\"snapshot\":\"25w01a\"},"
                + "\"versions\":[{\"id\":\"1.21.4\",\"type\":\"release\","
                + "\"url\":\"https://piston-meta.mojang.com/versions/1.21.4.json\","
                + "\"releaseTime\":\"2024-12-03T10:00:00Z\"},{"
                + "\"id\":\"25w01a\",\"type\":\"snapshot\","
                + "\"url\":\"https://piston-meta.mojang.com/versions/25w01a.json\","
                + "\"releaseTime\":\"2025-01-01T10:00:00Z\"}]}";
        Fake ok = new Fake(200, body.getBytes(StandardCharsets.UTF_8), -1);
        VersionManifestDownloader d = new VersionManifestDownloader(uri -> { eq(VersionManifestDownloader.MANIFEST_URI, uri, "fixed Mojang URL"); return ok; });
        try {
            VersionManifest downloaded = d.downloadManifest();
            eq("1.21.4", downloaded.getLatestRelease().getId(), "HTTP download decodes directly to a validated manifest");
            eq(2, downloaded.getCatalog().size(), "HTTP manifest version list is populated");
        } catch (IOException ex) { throw new AssertionError(ex); }
        eq(10_000, ok.connectTimeout, "connect timeout"); eq(15_000, ok.readTimeout, "read timeout");
        eq("GET", ok.method, "GET used"); eq("application/json", ok.headers.get("Accept"), "JSON Accept header");
        yes(!ok.followRedirects && ok.disconnected, "redirects disabled and connection closed");
        Fake status = new Fake(503, new byte[0], -1); io(() -> new VersionManifestDownloader(uri -> status).download(), "HTTP error rejected");
        Fake length = new Fake(200, new byte[0], VersionManifestDownloader.MAX_MANIFEST_BYTES + 1L); io(() -> new VersionManifestDownloader(uri -> length).download(), "large declared body rejected");
        byte[] oversized = new byte[VersionManifestDownloader.MAX_MANIFEST_BYTES + 1]; oversized[0] = '{';
        io(() -> new VersionManifestDownloader(uri -> new Fake(200, oversized, -1)).download(), "large streamed body rejected");
        io(() -> new VersionManifestDownloader(uri -> new Fake(200, new byte[0], 0)).download(), "empty body rejected");
        io(() -> new VersionManifestDownloader(uri -> new Fake(200, "gateway error".getBytes(StandardCharsets.UTF_8), -1)).download(), "non-JSON body rejected");
        io(() -> new VersionManifestDownloader(uri -> new Fake(200, new byte[] {(byte)0xC3, (byte)0x28}, -1)).download(), "malformed UTF-8 rejected");
        io(() -> new VersionManifestDownloader(uri -> null).download(), "null connection rejected");
        yes(status.disconnected && length.disconnected, "connections closed after errors");
    }

    private static void profiles() {
        yes(OfflineProfileValidator.isValid("Steve_123"), "valid profile accepted");
        yes(OfflineProfileValidator.isValid("abc") && OfflineProfileValidator.isValid("abcdefghijklmnop"), "length boundaries accepted");
        for (String s : new String[] {null, "ab", "abcdefghijklmnopq", "Bad Name", "éclair", "player-name"})
            yes(!OfflineProfileValidator.isValid(s), "invalid profile rejected: " + s);
        eq("Enter a username.", OfflineProfileValidator.validationMessage(""), "empty-name message");
        eq("", OfflineProfileValidator.validationMessage("Player_1"), "valid-name message empty");
    }

    private static void memory() {
        eq(296, MemoryDefaults.suggestedHeapMiB(768), "sub-1-GiB heap");
        eq(448, MemoryDefaults.suggestedHeapMiB(1024), "1-GiB heap");
        eq(448, MemoryDefaults.suggestedHeapMiB(1535), "1.5-GiB boundary below");
        eq(512, MemoryDefaults.suggestedHeapMiB(1536), "1.5-GiB heap");
        eq(512, MemoryDefaults.suggestedHeapMiB(2048), "2-GiB heap");
        eq(768, MemoryDefaults.suggestedHeapMiB(3072), "higher-RAM conservative heap");
        eq(1024, MemoryDefaults.suggestedHeapMiB(8192), "heap suggestion maximum");
        eq(384, MemoryDefaults.capToRuntimeMaximum(512, 384), "heap capped to runtime maximum");
        bad(() -> MemoryDefaults.suggestedHeapMiB(0), "zero RAM rejected");
        bad(() -> MemoryDefaults.capToRuntimeMaximum(512, 0), "zero runtime heap rejected");
    }

    private static MinecraftVersion version(String id, String type, String time) {
        return new MinecraftVersion(id, type, URI.create("https://piston-meta.mojang.com/versions/" + id + ".json"), time);
    }
    private static Map<String,Object> manifestObject() {
        Map<String,Object> root = new LinkedHashMap<>(), latest = new LinkedHashMap<>();
        latest.put("release", "1.21.4"); latest.put("snapshot", "25w01a"); root.put("latest", latest);
        Map<String,Object> release = new LinkedHashMap<>(), snapshot = new LinkedHashMap<>();
        release.put("id", "1.21.4"); release.put("type", "release"); release.put("url", "https://piston-meta.mojang.com/versions/1.21.4.json"); release.put("releaseTime", time1);
        snapshot.put("id", "25w01a"); snapshot.put("type", "snapshot"); snapshot.put("url", "https://piston-meta.mojang.com/versions/25w01a.json"); snapshot.put("releaseTime", "2025-01-01T10:00:00Z");
        root.put("versions", Arrays.asList(release, snapshot)); return root;
    }
    private static final class Fake implements VersionManifestDownloader.ManifestConnection {
        final int status; final byte[] body; final long length; int connectTimeout, readTimeout; boolean followRedirects = true, disconnected; String method; final Map<String,String> headers = new LinkedHashMap<>();
        Fake(int status, byte[] body, long length) { this.status = status; this.body = body; this.length = length; }
        public void setConnectTimeout(int n) { connectTimeout = n; }
        public void setReadTimeout(int n) { readTimeout = n; }
        public void setInstanceFollowRedirects(boolean b) { followRedirects = b; }
        public void setRequestMethod(String s) { method = s; }
        public void setRequestProperty(String k, String v) { headers.put(k, v); }
        public int getResponseCode() { return status; }
        public long getContentLengthLong() { return length; }
        public InputStream getInputStream() throws IOException { if (status != 200) throw new IOException("HTTP " + status); return new ByteArrayInputStream(body); }
        public void disconnect() { disconnected = true; }
    }
    @FunctionalInterface private interface Io { void run() throws IOException; }
    private static void io(Io op, String message) { checks++; try { op.run(); } catch (IOException expected) { return; } throw new AssertionError(message); }
    private static void bad(Runnable op, String message) { checks++; try { op.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError(message); }
    private static void unsupported(Runnable op, String message) { checks++; try { op.run(); } catch (UnsupportedOperationException expected) { return; } throw new AssertionError(message); }
    private static void eq(Object expected, Object actual, String message) { checks++; if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual); }
    private static void yes(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
}
