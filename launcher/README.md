# Launcher core and Android shell

Product-owned Java code lives here, separate from CI configuration and generated upstream runtime files.

## Implemented and tested

- Master manifest and per-version metadata download/JSON validation, including optional client Log4j configuration metadata.
- Client/library/asset/logging integrity metadata and deterministic installation planning.
- Safe HTTPS download/cache with progress/cancellation, exact size + SHA-1 verification and reusable verified files.
- Asset-index validation, content-addressed object paths and index-file verification; optional legacy virtual/resources layouts are materialized from verified objects with hash checks, safe paths and cache repair.
- Modern rule-based game/JVM arguments, legacy minecraftArguments parsing and an argv builder.
- Safe ZIP extraction with excludes, path traversal/symlink protection, entry/uncompressed-size limits and staging before destination replacement.
- Java process supervisor with argv-only invocation, output draining, cancellation and timeout; Java runtime probe checks the required Java major version.
- Android shell: version refresh, local profile validation, settings, base-file installation progress and cancellation.

## Android build

With JDK 17+, Gradle 8.9 and Android SDK platform/build-tools 35:

    cd launcher/android-app
    gradle :app:assembleDebug

CI assembles and verifies the debug APK. The screen can prepare supported base files, but it does not launch Minecraft yet.

## Next steps

Android runtime installation, compatible native selection, authenticated Microsoft account flow, launch supervision and real-device gameplay tests remain. These are tested foundations, not a claim that Minecraft is already playable.
