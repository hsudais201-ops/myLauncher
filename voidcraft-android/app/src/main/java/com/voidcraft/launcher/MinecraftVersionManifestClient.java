package com.voidcraft.launcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads public Minecraft: Java Edition version metadata from Mojang's
 * official version manifest. This class does not download game files.
 *
 * The implementation is first-party VoidCraft code and uses Android/Java APIs.
 */
public final class MinecraftVersionManifestClient {
    public static final String MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final int MAX_MANIFEST_BYTES = 8 * 1024 * 1024;
    private static final String OFFICIAL_MANIFEST_HOST = "piston-meta.mojang.com";

    private MinecraftVersionManifestClient() {
    }

    public static Manifest fetch() throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(MANIFEST_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "VoidCraftLauncher/0.2");

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Official version manifest returned HTTP " + status + ".");
            }

            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = readBounded(input, MAX_MANIFEST_BYTES);
                return parse(new String(bytes, StandardCharsets.UTF_8));
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Parses metadata for unit/regression tests and never retrieves game files. */
    public static Manifest parse(String json) throws IOException {
        if (json == null || json.trim().isEmpty()) {
            throw new IOException("The version manifest response was empty.");
        }
        if (json.length() > MAX_MANIFEST_BYTES) {
            throw new IOException("The version manifest exceeded the permitted size.");
        }

        try {
            JSONObject root = new JSONObject(json);
            JSONObject latest = root.optJSONObject("latest");
            JSONArray entries = root.optJSONArray("versions");
            if (latest == null || entries == null || entries.length() == 0) {
                throw new IOException("The version manifest is missing its expected fields.");
            }

            String latestRelease = requiredString(latest, "release");
            String latestSnapshot = requiredString(latest, "snapshot");
            List<Version> versions = new ArrayList<>(entries.length());
            Set<String> seenIds = new HashSet<>();

            for (int index = 0; index < entries.length(); index++) {
                JSONObject item = entries.optJSONObject(index);
                if (item == null) {
                    throw new IOException("The version manifest contains an invalid entry.");
                }

                String id = requiredString(item, "id");
                String type = requiredString(item, "type");
                String versionUrl = requiredString(item, "url");
                String releaseTime = requiredString(item, "releaseTime");
                String sha1 = item.optString("sha1", "");

                if (!id.matches("[A-Za-z0-9._+-]{1,100}")) {
                    throw new IOException("The version manifest contains an invalid version ID.");
                }
                if (!("release".equals(type)
                        || "snapshot".equals(type)
                        || "old_beta".equals(type)
                        || "old_alpha".equals(type))) {
                    throw new IOException("The version manifest contains an unknown release type.");
                }
                if (!isOfficialManifestUrl(versionUrl)) {
                    throw new IOException("A version entry points outside the official Mojang metadata host.");
                }
                if (!sha1.isEmpty() && !sha1.matches("[0-9a-fA-F]{40}")) {
                    throw new IOException("A version entry contains an invalid SHA-1 value.");
                }
                if (!seenIds.add(id)) {
                    throw new IOException("The version manifest contains duplicate version IDs.");
                }

                versions.add(new Version(id, type, releaseTime, sha1, versionUrl));
            }

            if (!seenIds.contains(latestRelease) || !seenIds.contains(latestSnapshot)) {
                throw new IOException("The latest release/snapshot IDs are missing from the version list.");
            }

            return new Manifest(latestRelease, latestSnapshot, versions);
        } catch (JSONException | IllegalArgumentException exception) {
            throw new IOException("The official version manifest could not be parsed.", exception);
        }
    }

    private static String requiredString(JSONObject object, String key) throws IOException {
        String value = object.optString(key, "").trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
            throw new IOException("The version manifest is missing the " + key + " field.");
        }
        return value;
    }

    private static boolean isOfficialManifestUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && OFFICIAL_MANIFEST_HOST.equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getPath() != null
                    && uri.getPath().startsWith("/v1/packages/");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > maxBytes) {
                throw new IOException("The version manifest exceeded the permitted size.");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    public static final class Manifest {
        public final String latestRelease;
        public final String latestSnapshot;
        public final List<Version> versions;

        private Manifest(String latestRelease, String latestSnapshot, List<Version> versions) {
            this.latestRelease = latestRelease;
            this.latestSnapshot = latestSnapshot;
            this.versions = Collections.unmodifiableList(new ArrayList<>(versions));
        }
    }

    public static final class Version {
        public final String id;
        public final String type;
        public final String releaseTime;
        public final String sha1;
        public final String metadataUrl;

        private Version(String id, String type, String releaseTime, String sha1, String metadataUrl) {
            this.id = id;
            this.type = type;
            this.releaseTime = releaseTime;
            this.sha1 = sha1;
            this.metadataUrl = metadataUrl;
        }
    }
}
