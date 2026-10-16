# Launcher core and Android shell

Product-owned Java code lives here, separated from CI configuration and generated upstream runtime files.

## Implemented and tested

- Master version manifest download and strict JSON decoding into a validated version catalog.
- Per-version details: client JAR integrity metadata, asset-index metadata, required Java version, library artifacts/classifiers, native mappings, ordered OS/feature rules, and legacy/modern game/JVM argument templates.
- Launch command builder resolves argument placeholders into a safe argv list (not a shell string), filters conditional args for the target platform, validates username/UUID/access-token inputs, and supports legacy minecraftArguments tokenization.
- Asset-index download with declared byte size and SHA-1 verification before JSON parsing; deterministic asset maps and content-addressed resource paths.
- Verified file downloader and installation service: exact HTTPS host allow-list, cancellation/progress, safe cache paths/no symlink components, size/SHA-1 verification before promotion, verified-cache reuse, and a deterministic download plan.
- Native Android shell: official version refresh, local profile validation, version/memory preferences, install progress/cancellation, and explicit status that the game runtime/login are not connected.

## Android build

The Android app project is under android-app/. With JDK 17+, Gradle 8.9 and Android SDK platform/build-tools 35 installed:

    cd launcher/android-app
    gradle :app:assembleDebug

CI assembles the debug APK and checks its package name. The current screen can prepare supported base files but does not launch Minecraft.

## Next steps

Add Android-compatible Java runtime/native extraction and actual process supervision, then integrate Microsoft account authentication. The launch argument builder is only an argv foundation: a compatible runtime, complete native classpath/extraction, valid authenticated account, and device tests are still required.
