# Launcher core and Android shell

Product-owned Java code lives here, separated from CI configuration and generated upstream runtime files.

## Implemented and tested

- Master version manifest download and strict JSON decoding into a validated version catalog.
- Per-version details: client JAR integrity metadata, asset-index metadata, required Java version, library artifacts/classifiers, native mappings, ordered OS/feature rules and extraction exclusions.
- Asset-index download with declared byte size and SHA-1 verification before JSON parsing; deterministic asset maps and content-addressed resource paths.
- Verified file downloader: exact HTTPS host allow-list, timeouts, disabled redirects, safe cache paths/no symlink components, temporary file writes, size and SHA-1 verification before atomic promotion, and verified-cache reuse.
- Deterministic installation-plan generation for the client JAR, platform-rule-matched ordinary library artifacts, and content-addressed asset objects. Identical output payloads are deduplicated; conflicting paths are rejected and unique download sizes are totaled.
- InstallationService orchestrates per-version metadata, SHA-1/size-verified asset-index download and persistence, then runs the plan using the verified-file cache. It emits immutable byte/task progress snapshots and checks cancellation between stages/tasks and during file transfer.
- Local profile-name validation, conservative memory defaults, and native Android shell with official version-list refresh and persisted version/profile/memory settings.

## Android build

The Android app project is under android-app/. With JDK 17+, Gradle 8.9 and Android SDK platform/build-tools 35 installed:

    cd launcher/android-app
    gradle :app:assembleDebug

CI assembles the debug APK and checks its package name. The current screen supports version refresh/selection, local profile-name validation and a conservative heap hint; the Launch button remains disabled until a real installation/runtime/authentication pipeline exists.

## Next steps

Connect InstallationService to an Android install progress/cancellation interface, persist install state, then add Android-compatible runtime/native-library support, launch arguments, Microsoft authentication and real-device testing. Native-only library classifiers are intentionally excluded from the current plan until Android-specific runtime handling exists.
