# Launcher core and Android shell

Product-owned Java code lives here, separate from CI configuration and generated upstream runtime files.

## Implemented and tested

- Master manifest and per-version metadata download/JSON validation.
- Client/library/asset integrity metadata and deterministic installation planning.
- Safe HTTPS download/cache with progress/cancellation, exact size + SHA-1 verification and reusable verified files.
- Asset-index validation, content-addressed object paths and index-file verification.
- Optional legacy virtual/resources layouts materialized from verified asset objects with SHA-1 checks, safe relative paths, no symlink traversal, corrupt-copy repair and cache reuse.
- Modern rule-based game/JVM arguments, legacy minecraftArguments parsing and a command builder that returns argv, not a shell string.
- Safe ZIP extraction with directory excludes, duplicate-file rejection, path traversal/symlink protection, entry and uncompressed-size limits, and staging before destination replacement.
- Java process supervisor with argv-only invocation, bounded output lines, stdout/stderr draining, cancellation, timeout and child termination.
- Java runtime probe that checks a candidate executable's reported major version against Minecraft metadata.
- Android shell: version refresh, local profile validation, settings, base-file installation progress and cancellation.

## Android build

With JDK 17+, Gradle 8.9 and Android SDK platform/build-tools 35:

    cd launcher/android-app
    gradle :app:assembleDebug

CI assembles and verifies the debug APK. The screen can prepare supported base files, but it does not launch Minecraft yet.

## Next steps

Android runtime installation and compatible native selection remain necessary. Wire the probe and process supervisor into a runtime manager, add licensed account authentication, then validate gameplay on real devices. These are foundations, not a claim that Minecraft already runs on Android.
