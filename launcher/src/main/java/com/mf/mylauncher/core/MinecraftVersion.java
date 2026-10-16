package com.mf.mylauncher.core;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable metadata for one Minecraft version from Mojang's version manifest. */
public final class MinecraftVersion {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}");
    private static final Pattern SAFE_TYPE = Pattern.compile("[a-z_]{1,32}");

    private final String id;
    private final String type;
    private final URI metadataUrl;
    private final Instant releaseTime;

    public MinecraftVersion(String id, String type, URI metadataUrl, String releaseTime) {
        this.id = validateId(id);
        this.type = validateType(type);
        this.metadataUrl = validateMetadataUrl(metadataUrl);
        this.releaseTime = parseReleaseTime(releaseTime);
    }

    private static String validateId(String value) {
        if (value == null || !SAFE_ID.matcher(value).matches() || value.contains("..")) {
            throw new IllegalArgumentException("Minecraft version ID is invalid");
        }
        return value;
    }

    private static String validateType(String value) {
        if (value == null || !SAFE_TYPE.matcher(value).matches()) {
            throw new IllegalArgumentException("Minecraft version type is invalid");
        }
        return value;
    }

    private static URI validateMetadataUrl(URI value) {
        if (value == null || !value.isAbsolute() || !"https".equalsIgnoreCase(value.getScheme())
                || value.getHost() == null || value.getUserInfo() != null) {
            throw new IllegalArgumentException("Version metadata URL must be an absolute HTTPS URL");
        }
        return value;
    }

    private static Instant parseReleaseTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Minecraft release time is required");
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Minecraft release time must be an ISO-8601 UTC timestamp", exception);
        }
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public URI getMetadataUrl() { return metadataUrl; }
    public Instant getReleaseTime() { return releaseTime; }
    public boolean isRelease() { return "release".equals(type); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MinecraftVersion)) return false;
        MinecraftVersion that = (MinecraftVersion) other;
        return id.equals(that.id) && type.equals(that.type)
                && metadataUrl.equals(that.metadataUrl) && releaseTime.equals(that.releaseTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, metadataUrl, releaseTime);
    }

    @Override
    public String toString() {
        return "MinecraftVersion{id='" + id + "', type='" + type + "'}";
    }
}
