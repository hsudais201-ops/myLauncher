# VoidCraft standalone Android shell

This directory is the first independently authored Android app module for the VoidCraft codebase migration.

## Build and tests

With JDK 17 and Android SDK platform 35 installed:

```sh
gradle --no-daemon testDebugUnitTest assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`. The version-browser commit passed the unit-test and APK-build steps in [GitHub Actions run #39398436918](https://github.com/hsudais201-ops/myLauncher/actions/runs/39398436918). It is a CI build/test result, not a physical-device or Minecraft launch test.

## Current independently authored components

- Kotlin Android app using platform Views; no Amethyst Activity/UI implementation is imported.
- Dashboard with working navigation to **Instances** and **Minecraft versions**.
- Profile metadata create/list/refresh and metadata-only removal. Removing an entry never deletes worlds, saves, mods, or game files.
- Version browser loads the official Mojang manifest off the UI thread, supports search and release/snapshot/old-beta/old-alpha filters, fetches details on selection, and can create **profile metadata only**.
- Version manifest and detail parser validates identifiers, timestamps, hashes, allowed HTTPS hosts, client download metadata, and asset-index metadata. Version-detail JSON is checked against the SHA-1 supplied by the manifest.
- Bounded HTTPS metadata fetching validates redirect targets and response sizes.
- Bounded HTTPS file downloader supports cancellation, transient HTTP retries, declared/actual size checks, optional SHA-1/SHA-256 verification, temporary-file staging, and destination replacement only after validation.

## Important limitations

- The version browser does **not** install Minecraft files or launch a game. Creating a profile only records the intended version and profile name.
- The file downloader is a reusable service and is not yet connected to a complete installation workflow. Failed downloads retry from the beginning; partial downloads are not resumed and no queue persists across app restarts.
- A full installer still needs library/asset dependency resolution, inherited-version merging, Java runtime selection/acquisition, install/repair orchestration, and verification of all required files.
- Microsoft/Xbox/Minecraft authentication, Java/native runtime integration, rendering/input, feature-store installation, modpack import/export, and the touch-control designer are not connected.
- There is no discovery or migration of data from the existing launcher. This module does not read or modify existing launcher data.
- No physical-device test is claimed. Automated tests/builds do not prove Android device compatibility or Minecraft launch capability.
- The old Amethyst-based build remains unchanged while the replacement is developed.

The core modules use Gson 2.11.0 for JSON parsing (Apache-2.0); see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). A complete APK SBOM and distribution audit are still required.
