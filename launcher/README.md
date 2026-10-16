# Launcher core

This folder contains myLauncher-owned Java code, kept separate from build automation and generated upstream runtime files.

## Current milestone

- MinecraftVersion and VersionCatalog validate and index master-manifest version metadata.
- VersionManifestJsonDecoder parses Mojang's downloaded manifest JSON without a third-party dependency.
- VersionDetailsJsonDecoder and VersionDetailsMapper parse per-version client JAR, asset-index, Java major version, library artifacts/classifiers, native mappings, ordered OS/feature rules, and native extraction exclusions.
- DownloadArtifact validates HTTPS URLs, SHA-1 hashes, sizes, and safe relative destination paths. LibraryRule implements ordered last-matching-rule behavior.
- VersionManifestDownloader has connect/read timeouts, no automatic redirects, strict UTF-8, and an 8 MiB cap. VersionDetailsDownloader fetches per-version JSON only from exact Mojang metadata hosts and approved JSON paths.
- OfflineProfileValidator validates a local display/profile name. It is not Microsoft authentication and does not provide a licensed Minecraft account.
- MemoryDefaults chooses conservative heap defaults and caps the suggestion to the runtime's reported maximum.

The Android UI, asset-index parsing/object resolution, verified file downloads/cache, account-token flow, native runtime integration, and actual game launch pipeline remain future work. This code is a core foundation, not yet a playable Android launcher.

## Test

Requires a JDK. Run:

./launcher/test.sh

Tests compile all core classes with -Xlint:all -Werror into a temporary directory and run the dependency-free regression suite. Tests use fake network connections; they do not contact Mojang or prove that a real device can launch the game.
