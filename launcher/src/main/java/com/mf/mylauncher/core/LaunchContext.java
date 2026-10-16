package com.mf.mylauncher.core;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Launch-time values; access tokens are supplied by a future authenticated account provider. */
public final class LaunchContext {
    private final String username, uuid, accessToken, userType, versionType;
    private final String gameDirectory, assetsRoot, librariesDirectory, nativesDirectory, classpath;
    private final String launcherName, launcherVersion, clientId, xuid, userPropertiesJson, loggingConfigPath;
    private final int resolutionWidth, resolutionHeight, minHeapMiB, maxHeapMiB;
    private final LibraryPlatform platform;

    private LaunchContext(Builder builder) {
        if (!OfflineProfileValidator.isValid(builder.username)) throw new IllegalArgumentException("Launch username is invalid");
        if (builder.uuid == null || !builder.uuid.replace("-", "").matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("Authenticated profile UUID must contain 32 hexadecimal characters");
        }
        if (builder.accessToken == null || builder.accessToken.isEmpty()) {
            throw new IllegalArgumentException("A non-empty access token is required to build a game launch command");
        }
        if (builder.platform == null) throw new IllegalArgumentException("Launch platform is required");
        this.username = builder.username;
        this.uuid = builder.uuid.replace("-", "").toLowerCase(java.util.Locale.ROOT);
        this.accessToken = builder.accessToken;
        this.userType = nonEmptyOrDefault(builder.userType, "msa");
        this.versionType = nonEmptyOrDefault(builder.versionType, "release");
        this.gameDirectory = requirePath(builder.gameDirectory, "game directory");
        this.assetsRoot = requirePath(builder.assetsRoot, "assets directory");
        this.librariesDirectory = requirePath(builder.librariesDirectory, "libraries directory");
        this.nativesDirectory = requirePath(builder.nativesDirectory, "natives directory");
        if (builder.classpath == null || builder.classpath.trim().isEmpty()) throw new IllegalArgumentException("Resolved classpath is required");
        this.classpath = builder.classpath;
        this.launcherName = nonEmptyOrDefault(builder.launcherName, "myLauncher");
        this.launcherVersion = nonEmptyOrDefault(builder.launcherVersion, "0.1.0");
        this.clientId = builder.clientId == null ? "" : builder.clientId;
        this.xuid = builder.xuid == null ? "" : builder.xuid;
        this.userPropertiesJson = builder.userPropertiesJson == null ? "{}" : builder.userPropertiesJson;
        this.loggingConfigPath = builder.loggingConfigPath == null ? "" : builder.loggingConfigPath;
        if (builder.resolutionWidth <= 0 || builder.resolutionHeight <= 0) throw new IllegalArgumentException("Window resolution values must be positive");
        this.resolutionWidth = builder.resolutionWidth;
        this.resolutionHeight = builder.resolutionHeight;
        if (builder.minHeapMiB < 64 || builder.maxHeapMiB < builder.minHeapMiB) throw new IllegalArgumentException("Heap settings must be positive and maximum must be at least minimum");
        this.minHeapMiB = builder.minHeapMiB;
        this.maxHeapMiB = builder.maxHeapMiB;
        this.platform = builder.platform;
    }

    private static String requirePath(Path path, String label) {
        if (path == null) throw new IllegalArgumentException(label + " path is required");
        return path.toAbsolutePath().normalize().toString();
    }

    private static String nonEmptyOrDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    Map<String, String> variables(VersionDetails details) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("auth_player_name", username);
        variables.put("auth_uuid", uuid);
        variables.put("auth_access_token", accessToken);
        variables.put("user_type", userType);
        variables.put("version_name", details.getId());
        variables.put("version_type", versionType);
        variables.put("game_directory", gameDirectory);
        variables.put("assets_root", assetsRoot);
        variables.put("assets_index_name", details.getAssetIndex().getId());
        variables.put("library_directory", librariesDirectory);
        variables.put("classpath", classpath);
        variables.put("classpath_separator", java.io.File.pathSeparator);
        variables.put("natives_directory", nativesDirectory);
        variables.put("launcher_name", launcherName);
        variables.put("launcher_version", launcherVersion);
        variables.put("user_properties", userPropertiesJson);
        variables.put("clientid", clientId);
        variables.put("auth_xuid", xuid);
        variables.put("resolution_width", Integer.toString(resolutionWidth));
        variables.put("resolution_height", Integer.toString(resolutionHeight));
        variables.put("path", loggingConfigPath);
        return variables;
    }

    public String getUsername() { return username; }
    public String getUuid() { return uuid; }
    public String getAccessToken() { return accessToken; }
    public Path getGameDirectoryPath() { return java.nio.file.Paths.get(gameDirectory); }
    public Path getAssetsRootPath() { return java.nio.file.Paths.get(assetsRoot); }
    public Path getLibrariesDirectoryPath() { return java.nio.file.Paths.get(librariesDirectory); }
    public Path getNativesDirectoryPath() { return java.nio.file.Paths.get(nativesDirectory); }
    public String getClasspath() { return classpath; }
    public String getLoggingConfigPath() { return loggingConfigPath; }
    public int getMinHeapMiB() { return minHeapMiB; }
    public int getMaxHeapMiB() { return maxHeapMiB; }
    public LibraryPlatform getPlatform() { return platform; }

    public static final class Builder {
        private final String username, uuid, accessToken;
        private Path gameDirectory, assetsRoot, librariesDirectory, nativesDirectory;
        private String classpath;
        private String userType = "msa", versionType = "release";
        private String launcherName = "myLauncher", launcherVersion = "0.1.0";
        private String clientId = "", xuid = "", userPropertiesJson = "{}", loggingConfigPath = "";
        private int resolutionWidth = 854, resolutionHeight = 480, minHeapMiB = 128, maxHeapMiB = 512;
        private LibraryPlatform platform;

        public Builder(String username, String uuid, String accessToken) {
            this.username = username; this.uuid = uuid; this.accessToken = accessToken;
        }
        public Builder gameDirectory(Path value) { gameDirectory = value; return this; }
        public Builder assetsRoot(Path value) { assetsRoot = value; return this; }
        public Builder librariesDirectory(Path value) { librariesDirectory = value; return this; }
        public Builder nativesDirectory(Path value) { nativesDirectory = value; return this; }
        public Builder classpath(String value) { classpath = value; return this; }
        public Builder userType(String value) { userType = value; return this; }
        public Builder versionType(String value) { versionType = value; return this; }
        public Builder launcherName(String value) { launcherName = value; return this; }
        public Builder launcherVersion(String value) { launcherVersion = value; return this; }
        public Builder clientId(String value) { clientId = value; return this; }
        public Builder xuid(String value) { xuid = value; return this; }
        public Builder userPropertiesJson(String value) { userPropertiesJson = value; return this; }
        public Builder loggingConfigPath(String value) { loggingConfigPath = value; return this; }
        public Builder resolution(int width, int height) { resolutionWidth = width; resolutionHeight = height; return this; }
        public Builder heapMiB(int min, int max) { minHeapMiB = min; maxHeapMiB = max; return this; }
        public Builder platform(LibraryPlatform value) { platform = value; return this; }
        public LaunchContext build() { return new LaunchContext(this); }
    }
}
