# Launcher core and Android shell

Product-owned Java code lives here, separate from CI configuration and generated upstream runtime files.

## Implemented and tested

- Master manifest and per-version metadata download/JSON validation, including client logging configuration metadata.
- Client/library/asset integrity metadata and deterministic install planning; optional logging config is included as a separate verified download task.
- Safe HTTPS download/cache with progress/cancellation, exact size + SHA-1 verification and reusable verified files.
- Asset-index validation, content-addressed object paths, index verification, and safe materialization of virtual/resources layouts.
- Modern rule-based game/JVM arguments, legacy `minecraftArguments` parsing, verified on-disk classpath resolution in metadata order, and an argv builder that avoids shell concatenation.
- Launch preflight and coordinator: verify client/library and asset-index integrity, enforce the required Java major version, validate the game/assets/libraries/natives directory layout, check the context classpath and logging config, expand arguments, and supervise the child process.
- Explicit-target native classifier planning and optional install-service integration: archive downloads are checksum verified, archive output paths are checked, and multiple native archives are extracted into a staged version-specific directory with per-archive exclusions and cancellation.
- Safe single- and multi-archive ZIP extraction with path traversal/symlink protection, entry/uncompressed-size limits, duplicate-file rejection, and staged destination replacement.
- Java process supervisor, runtime major-version probe, and lookup for an already-installed executable under the app-private `runtimes/<id>/bin/java` layout.
- Android shell: version refresh, local profile validation, settings, base-file installation progress/cancellation, and a runtime-presence check.

## Android build

With JDK 17+, Gradle 8.9 and Android SDK platform/build-tools 35:

    cd launcher/android-app
    gradle :app:assembleDebug

CI assembles and verifies the debug APK. The screen can prepare supported base files and inspect the expected runtime location, but it does not launch Minecraft yet.

## Next steps

The main blockers are an Android-compatible Java runtime provider, Android-specific native library selection/loading and graphics compatibility, and authenticated Microsoft account flow. The opt-in native plan must only be used with binaries compatible with the selected runtime/ABI. After those pieces are integrated, connect Android authentication and runtime packaging, then validate actual Minecraft gameplay on real devices.
