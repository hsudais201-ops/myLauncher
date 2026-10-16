# Launcher core and Android shell

Product-owned Java code lives here, separate from CI configuration and generated upstream runtime files.

## Implemented and tested

- Master manifest and per-version metadata download/JSON validation, including client logging configuration metadata.
- Client/library/asset integrity metadata and deterministic install planning; optional logging config is included as a separate verified download task.
- Safe HTTPS download/cache with progress/cancellation, exact size + SHA-1 verification and reusable verified files.
- Asset-index validation, content-addressed object paths, index verification, and safe materialization of virtual/resources layouts.
- Modern rule-based game/JVM arguments, legacy minecraftArguments parsing, verified on-disk classpath resolution in metadata order, and a builder that returns an argv list rather than a shell string.
- Explicit-target native classifier planning and optional install-service integration: archive downloads are checksum verified, archive output paths are checked, and multiple native archives are extracted into a staged version-specific directory with per-archive exclusions.
- Safe single- and multi-archive ZIP extraction with path traversal/symlink protection, entry/uncompressed-size limits, duplicate-file rejection, and staged destination replacement.
- Java process supervisor and runtime major-version probe.
- Android shell: version refresh, local profile validation, settings, base-file installation progress and cancellation.

## Android build

With JDK 17+, Gradle 8.9 and Android SDK platform/build-tools 35:

    cd launcher/android-app
    gradle :app:assembleDebug

CI assembles and verifies the debug APK. The screen can prepare supported base files, but it does not launch Minecraft yet.

## Next steps

Android-specific runtime and native-library compatibility remains the main blocker before process launch. The native plan is opt-in and should only be used when the caller has selected binaries compatible with its target runtime/ABI. Then integrate the verified classpath resolver and launch-command builder with a compatible runtime and licensed account flow, and validate on real devices.
