# VoidCraft standalone Android shell

This directory is the first independently authored Android app module for the VoidCraft codebase migration.

## Build and tests

With JDK 17 and Android SDK platform 35 installed:

```sh
gradle --no-daemon testDebugUnitTest assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`. The profile catalog previously passed its unit-test and APK-build step in [GitHub Actions run #39359802263](https://github.com/hsudais201-ops/myLauncher/actions/runs/39359802263). The current branch adds version metadata and downloader tests; see the current [Actions checks](https://github.com/hsudais201-ops/myLauncher/actions).

## Current independently authored components

- Kotlin Android app using platform Views; no Amethyst Activity/UI implementation is imported.
- Dashboard with a working link to the Instances screen.
- Profile metadata create/list/refresh and metadata-only removal. Removal never deletes world, save, mod, or game files.
- Version manifest parser for release/snapshot/old-version metadata, version detail parsing for client download and asset-index metadata, and a catalog client for the official Mojang version manifest.
- Bounded HTTPS text fetch with host validation and redirect validation for metadata requests.
- Bounded HTTPS file downloader with cancellation, transient HTTP retry, declared/actual size checks, optional SHA-1/SHA-256 verification, temporary-file staging, and destination promotion only after validation.

## Important limitations

- The metadata and download services are core code only; they are **not connected to the dashboard or a version-installation screen**.
- The downloader retries from the beginning; it does not resume partial downloads or persist a download queue across app restarts.
- This is not yet a complete Minecraft installer. Library and asset dependency resolution, inherited-version merging, Java-runtime acquisition, game-file repair orchestration, Microsoft/Xbox/Minecraft authentication, runtime launch, rendering/input, feature-store installation, modpack import/export, and the touch-control designer are not connected.
- Profile records are metadata only. They do not mean that the selected Minecraft version is installed or launchable.
- There is no discovery or migration of data from the existing launcher. This module does not read or modify existing launcher data.
- No physical-device test is claimed. Automated tests/builds do not prove Android device compatibility or Minecraft launch capability.
- The old Amethyst-based build remains unchanged while the replacement is developed.

The core modules use Gson 2.11.0 for JSON parsing (Apache-2.0); see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). A complete APK SBOM and distribution audit are still required.
