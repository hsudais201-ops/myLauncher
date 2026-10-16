# Launcher core

This folder contains myLauncher-owned Java source, separate from CI configuration and generated upstream runtime files.

## Implemented and tested

- Master version manifest download and strict JSON decoding into a validated version catalog.
- Per-version details: client JAR integrity metadata, asset-index metadata, required Java version, library artifacts/classifiers, native mappings, ordered OS/feature rules and extraction exclusions.
- Asset-index download with declared byte size and SHA-1 verification before JSON parsing.
- Asset-index JSON decoding, deterministic object ordering, content-addressed object paths, official resource CDN URL generation, logical resource paths, and total-size accounting.
- Verified file downloader: exact HTTPS host allow-list, timeouts, disabled redirects, safe cache paths/no symlink components, temporary file writes, size and SHA-1 verification before atomic promotion, and verified-cache reuse.
- Local profile-name validation and conservative memory defaults.

The Android UI, settings, Microsoft auth, runtime/native integration, installation orchestration, and in-game launch flow are still required. This is not yet a playable Android APK.

## Test

Requires a JDK. Run:

    ./launcher/test.sh

Regression tests use fake HTTP connections and temporary directories. They verify valid downloads, cache hits/corruption repair, metadata-size/hash failures, path traversal/symlink rejection and partial-file cleanup. They do not download production game files or run Minecraft on a device.
