# Launcher core and Android shell

Product-owned Java code lives here, separate from CI configuration and generated upstream runtime files.

## Implemented and tested

- Master manifest and per-version metadata download/JSON validation.
- Client/library/asset integrity metadata and deterministic installation planning.
- Safe HTTPS download/cache with progress/cancellation, exact size + SHA-1 verification and reusable verified files.
- Asset-index validation, content-addressed object paths and index-file verification.
- Modern rule-based game/JVM arguments, legacy minecraftArguments parsing and a command builder that returns an argv list rather than a shell string.
- Safe ZIP extraction with directory excludes, duplicate-file rejection, path traversal/symlink protection, entry and uncompressed-size limits, and staging before destination replacement.
- Java process supervisor with argv-only invocation, bounded output lines, stdout/stderr draining, cancellation, timeout and child termination.
- Android shell: version refresh, local profile validation, settings, base-file installation progress and cancellation.

## Android build

With JDK 17+, Gradle 8.9 and Android SDK platform/build-tools 35:

    cd launcher/android-app
    gradle :app:assembleDebug

CI assembles and verifies the debug APK. The screen can prepare supported base files, but it does not launch Minecraft yet.

## Next steps

Android runtime/native integration must select compatible native libraries and use the safe extractor. Then wire the process supervisor to an Android-compatible runtime and launch arguments, integrate a licensed account flow, and test on devices. These components are tested building blocks, not proof of working gameplay.
