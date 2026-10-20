# VoidCraft standalone Android shell

This directory is the first independently authored Android app module for the VoidCraft codebase migration.

## Build and tests

With JDK 17 and Android SDK platform 35 installed:

```sh
gradle --no-daemon testDebugUnitTest assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`. The independently authored version-browser commit passed JVM tests and APK build in [run #39399012705](https://github.com/hsudais201-ops/myLauncher/actions/runs/39399012705), and the library/asset planning tests passed in [run #39413275839](https://github.com/hsudais201-ops/myLauncher/actions/runs/39413275839). The transactional executor passed tests and debug APK build in [run #39413869771](https://github.com/hsudais201-ops/myLauncher/actions/runs/39413869771). The current commit adds additional path-scope checks that require a fresh CI result.

## Current independently authored components

- Kotlin Android app using platform Views; no Amethyst Activity/UI implementation is imported.
- Dashboard with working navigation to **Instances** and **Minecraft versions**.
- Profile metadata create/list/refresh and metadata-only removal. Removing an entry never deletes worlds, saves, mods, or game files.
- Version browser loads the official Mojang manifest off the UI thread, supports search and release/snapshot/old-beta/old-alpha filters, fetches details on selection, and can create profile metadata only.
- Version metadata parser checks the version-detail JSON against the manifest SHA-1 and extracts client/asset-index/library metadata.
- Asset-index parser validates object hashes/sizes and constructs content-addressed asset paths.
- Install planner resolves client metadata, libraries, OS/architecture rules, native classifiers, asset-index objects and secure relative destinations into a deterministic download plan.
- Transactional executor stages and verifies all planned downloads first, then backs up/replaces destination files and rolls back if a commit step fails.
- Bounded HTTPS file downloader supports progress, cancellation, transient HTTP retries, byte-size limits, optional artifact SHA-1/SHA-256 verification, temporary-file staging, and destination replacement only after validation.

## Important limitations

- The version browser does **not** start installation. The executor exists as core service code but is not connected to the UI.
- Executor requires a warning-free plan. The planner reports unresolved inherited metadata or missing asset-index parsing rather than claiming readiness.
- Native extraction, recursive inherited-version resolution/merging, Java runtime setup, and Android-compatible renderer/runtime integration are still unimplemented.
- Failed downloads retry from the beginning; partial downloads are not resumed and no queue persists across app restarts.
- This is not yet a complete Minecraft launcher. Microsoft/Xbox/Minecraft authentication, Java/native runtime launch, rendering/input, feature-store installation, modpack import/export, touch-control designer, and existing-launcher data migration are not connected.
- No physical-device test is claimed. Automated tests/builds do not prove Android device compatibility or Minecraft launch capability.
- The old Amethyst-based build remains unchanged while the replacement is developed.

The core modules use Gson 2.11.0 for JSON parsing (Apache-2.0); see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). A complete APK SBOM and distribution audit are still required.
