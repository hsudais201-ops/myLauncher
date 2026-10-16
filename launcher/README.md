# Launcher core and Android shell

Product-owned Java code lives here, separated from CI configuration and generated upstream runtime files.

## Implemented and tested

- Master version manifest download and strict JSON decoding into a validated version catalog.
- Per-version details: client JAR integrity metadata, asset-index metadata, required Java version, library artifacts/classifiers, native mappings, ordered OS/feature rules and extraction exclusions.
- Asset-index download with declared byte size and SHA-1 verification before JSON parsing; deterministic asset maps and content-addressed resource paths.
- Verified file downloader: exact HTTPS host allow-list, timeouts, disabled redirects, safe cache paths/no symlink components, temporary file writes, size and SHA-1 verification before atomic promotion, and verified-cache reuse.
- Local profile-name validation and conservative memory defaults.
- Native Android shell with official version-list refresh, persistent version/profile/memory settings, and clear messaging that game launching is not wired up yet.

## Android build

The Android app project is under android-app/. With JDK 17+, Gradle 8.9 and Android SDK platform/build-tools 35 installed:

    cd launcher/android-app
    gradle :app:assembleDebug

CI assembles the debug APK and checks that its package name is com.mf.mylauncher. The Android screen currently loads the Mojang version manifest asynchronously, supports version selection, validates a local profile name and stores a conservative heap hint.

## Next steps

Connect the verified download/cache layer to version installation, then add Android-compatible Java runtime and native-library support, argument construction, Microsoft authentication and real-device launch tests. The current code does not yet launch Minecraft.
