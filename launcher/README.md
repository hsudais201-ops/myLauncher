# Launcher core

This folder contains myLauncher-owned Java source, separate from CI configuration and any upstream game runtime.

## Implemented and tested

- Master manifest download and strict JSON decoding into a validated version catalog.
- Per-version details: client JAR integrity metadata, asset-index location, Java major version, Maven-coordinate libraries, download classifiers, native mappings, OS/feature rules and native extraction excludes.
- Asset-index JSON decoding, deterministic object ordering, content-addressed object paths, official resource CDN URL generation, logical resource paths and total-size accounting.
- Validated artifact SHA-1/size/HTTPS metadata and safe relative destination paths.
- Local offline profile-name validation and conservative RAM-based heap defaults.

The actual file download/cache layer is the next milestone. It must write into temporary files and only promote files after exact size and SHA-1 validation. Android UI, authentication, native runtime, and actual game launch are not yet implemented, so this is not a playable launcher yet.

## Test

Requires a JDK. Run:

    ./launcher/test.sh

Tests compile all core classes with warnings as errors and run dependency-free regression checks. The current test suite uses fake network connections; it does not download game files or run Minecraft on an Android device.
