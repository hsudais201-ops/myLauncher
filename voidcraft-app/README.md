# VoidCraft standalone Android shell

This directory is the first independently authored Android app module for the VoidCraft codebase migration.

## Build and tests

With JDK 17 and Android SDK platform 35 installed:

```sh
gradle --no-daemon testDebugUnitTest assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`. The branch-head JVM tests and Android debug APK build previously passed in [GitHub Actions run #39399012705](https://github.com/hsudais201-ops/myLauncher/actions/runs/39399012705). The current commit adds library and asset planning, so its own CI result must be checked separately.

## Current independently authored components

- Kotlin Android app using platform Views; no Amethyst Activity/UI implementation is imported.
- Dashboard with working navigation to **Instances** and **Minecraft versions**.
- Profile metadata create/list/refresh and metadata-only removal. Removing an entry never deletes world, save, mod, or game files.
- Version browser loads the official Mojang manifest off the UI thread, supports search and release/snapshot/old-beta/old-alpha filters, fetches details on selection, and can create profile metadata only.
- Version metadata parser checks the version-detail JSON against the manifest SHA-1 and extracts client/asset-index/library metadata.
- Asset-index parser validates object hashes/sizes and constructs content-addressed asset paths.
- Install planner resolves client metadata, libraries, OS/architecture rules, native classifiers, asset-index objects and secure relative destinations into a deterministic download plan. It reports inherited metadata or missing asset-index resolution rather than claiming the plan is launch-ready.
- Bounded HTTPS file downloader supports progress, cancellation, transient HTTP retries, byte-size limits, optional artifact SHA-1/SHA-256 verification, temporary-file staging, and destination replacement only after validation.

## Important limitations

- The version browser does **not** install Minecraft files or launch a game. Creating a profile only records the intended version and profile name.
- The install planner emits download tasks but does not execute them, unpack native libraries, or set up an Android runtime.
- Inherited/loader version metadata must still be resolved and merged before a complete plan can be considered ready.
- Failed downloads retry from the beginning; partial downloads are not resumed and no queue persists across app restarts.
- This is not yet a complete Minecraft installer. Runtime selection/acquisition, library/asset download orchestration and repair, Microsoft/Xbox/Minecraft authentication, Java/native runtime launch, rendering/input, feature-store installation, modpack import/export, and the touch-control designer are not connected.
- There is no discovery or migration of data from the existing launcher. This module does not read or modify existing launcher data.
- No physical-device test is claimed. Automated tests/builds do not prove Android device compatibility or Minecraft launch capability.
- The old Amethyst-based build remains unchanged while the replacement is developed.

The core modules use Gson 2.11.0 for JSON parsing (Apache-2.0); see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). A complete APK SBOM and distribution audit are still required.
