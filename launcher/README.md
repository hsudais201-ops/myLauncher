# Launcher core

This folder contains myLauncher-owned code, kept separate from Android build automation and from the upstream game runtime.

## Current milestone

- `MinecraftVersion` validates manifest version metadata, including path-safe IDs, timestamps, and HTTPS metadata URLs.
- `VersionCatalog` indexes versions, sorts newest-first, and provides release/snapshot lookup helpers.
- `OfflineProfileValidator` validates a local display/profile name. This is **not** Microsoft authentication and does not provide a licensed Minecraft account.
- `MemoryDefaults` selects conservative heap defaults and caps the suggestion to the runtime's reported maximum.

The Android UI, official manifest JSON adapter, library/assets downloads, account-token flow, native runtime integration, and actual game launch pipeline are later milestones. A catalog model alone does not download or run Minecraft.

## Test

Requires only a JDK (Java 8+ APIs are used by the core source and the repository CI runs JDK 21):

```sh
./launcher/test.sh
```

Tests compile all core classes with `-Xlint:all -Werror` into a temporary directory and run the dependency-free regression suite. The script removes its temporary output when it exits.
- `VersionManifest` and `VersionManifestMapper` validate the manifest's latest release/snapshot pointers and convert a decoded JSON object graph into the catalog. The Android adapter will use the upstream Gson dependency to decode the HTTP response before passing the map to the mapper; the core itself has no third-party dependency.
- `VersionManifestDownloader` fetches Mojang's fixed HTTPS manifest endpoint with explicit connect/read timeouts, no automatic redirects, strict UTF-8 decoding, and an 8 MiB response cap. It returns JSON text; decode it with the Android/upstream JSON library, then pass the decoded map to `VersionManifestMapper`.
