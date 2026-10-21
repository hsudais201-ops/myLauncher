# VoidCraft standalone Android shell

This directory is the first independently authored Android app module for the VoidCraft codebase migration.

## Build and tests

With JDK 17 and Android SDK platform 35 installed:

```sh
gradle --no-daemon testDebugUnitTest assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`. The independently authored version-browser commit passed JVM tests and APK build in [run #39399012705](https://github.com/hsudais201-ops/myLauncher/actions/runs/39399012705), and the library/asset planning tests passed in [run #39413275839](https://github.com/hsudais201-ops/myLauncher/actions/runs/39413275839). The transactional executor passed tests and debug APK build in [run #39413869771](https://github.com/hsudais201-ops/myLauncher/actions/runs/39413869771). The artifact path/URL restrictions passed in [run #39414247303](https://github.com/hsudais201-ops/myLauncher/actions/runs/39414247303), and the verification/cache layer passed in [run #39414960675](https://github.com/hsudais201-ops/myLauncher/actions/runs/39414960675). Inheritance resolution passed tests and APK build in [run #39416168765](https://github.com/hsudais201-ops/myLauncher/actions/runs/39416168765). The latest native extraction/assembly and launch-argument work must pass its current GitHub Actions test/build run before it is treated as verified.

## Current independently authored components

- Kotlin Android app using platform Views; no Amethyst Activity/UI implementation is imported.
- Dashboard with working navigation to **Instances** and **Minecraft versions**.
- Profile metadata create/list/refresh and metadata-only removal. Removing an entry never deletes worlds, saves, mods, or game files.
- Version browser loads the official Mojang manifest off the UI thread, supports search and release/snapshot/old-beta/old-alpha filters, fetches details on selection, and can create profile metadata only.
- Version metadata parser checks the version-detail JSON against the manifest SHA-1 and extracts client/asset-index/library metadata.
- Asset-index parser validates object hashes/sizes and constructs content-addressed asset paths.
- Inheritance resolver recursively follows parent metadata with cycle/depth limits, merges currently modeled metadata, and tracks the version directory containing an inherited client JAR.
- Install-preparation service combines parent resolution, asset-index SHA-1/size verification, and install-plan creation; it reports warnings and does not download game artifacts.
- Install planner resolves client metadata, libraries, OS/architecture rules, native classifiers, asset-index objects and secure relative destinations into a deterministic download plan.
- Read-only install verification scan reports missing, size-mismatched, checksum-mismatched, unverifiable, and verified artifacts.
- Transactional executor reuses SHA-1-verified files, stages only missing/corrupt artifacts, then backs up/replaces destination files and rolls back if a commit step fails.
- Artifact-install orchestrator sequences metadata preparation and executor download/verification, reports stage/progress callbacks, checks cancellation, and refuses incomplete or version-mismatched plans before downloading.
- Bounded HTTPS file downloader supports progress, cancellation, transient HTTP retries, byte-size limits, optional artifact SHA-1/SHA-256 verification, temporary-file staging, and destination replacement only after validation.
- Native archive extractor validates archive paths, applies metadata exclusions, bounds expansion sizes/ratios, supports cancellation, and publishes only to a new directory.
- Native library assembler combines archives, reuses byte-identical duplicates, rejects conflicting duplicate paths, and bounds combined output size.
- Modern launch-argument resolver applies metadata platform rules and expands named placeholders without splitting arguments or recursively expanding substituted values.

## Important limitations

- The version browser does **not** start installation. An artifact-install orchestration service now connects preparation to the executor in the core layer, but it is not connected to the UI and does not establish that the installed artifacts can launch on Android.
- Executor requires a warning-free plan. The planner reports unresolved inherited metadata or missing asset-index parsing rather than claiming readiness.
- Native extraction/assembly and modern launch-argument interpolation now exist as tested core components, but are not connected to the install executor or a launch context. Logging integration, complete classpath/JVM/game argument assembly, legacy arguments, loader-specific metadata, persistent queue/resume, Java runtime setup, and Android-compatible renderer/runtime integration remain unfinished.
- Failed downloads retry from the beginning; partial downloads are not resumed and no queue persists across app restarts.
- This is not yet a complete Minecraft launcher. Microsoft/Xbox/Minecraft authentication, Java/native runtime launch, rendering/input, online feature-package installation, modpack import/export, touch-control designer, and existing-launcher data migration are not connected.
- No physical-device test is claimed. Automated tests/builds do not prove Android device compatibility or Minecraft launch capability.
- The old Amethyst-based build remains unchanged while the replacement is developed.

The core modules use Gson 2.11.0 for JSON parsing (Apache-2.0); see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). A complete APK SBOM and distribution audit are still required.


## Feature Store security milestone

The independent Android app now has a shared first-party Feature Store catalog with 23 entries and a fail-closed local registry. The original 11 catalog entries are joined by 12 additions: Resource Pack Manager, Resource Pack Compatibility Checker, World Details Viewer, Playtime Tracker, Accessibility Presets, Profile Icon Studio, Configuration Backup, Network Diagnostics, Minecraft Release Watch, Controller Presets, Resource Usage History, and Instance Notes. These additions are catalog concepts marked planned, not shipped capabilities. The Feature Store screen reads from that catalog, displays declared capabilities, and explains why a feature is unavailable rather than simulating a successful install. Unknown IDs, traversal-like IDs, planned features, and community submissions are not installable. The registry never loads or executes downloaded add-on code.

This is deliberately a security foundation, not a finished online store. The built-in Startup Diagnostics, Instance Notes, and Network Diagnostics pages are the three installable features at this stage. It reads device/app memory and storage information, reports the current runtime integration status, and allows the user to copy a report. Instance Notes stores up to 5,000 characters of private note text per profile, supports saving/editing/deleting notes, and keeps notes when the optional feature is uninstalled. It does not modify game files or world saves. Neither built-in feature changes device/game settings or executes downloaded code. Network Diagnostics only performs a user-triggered TCP connect to the supplied host and port; its result does not establish Minecraft protocol status. Signed manifests, package integrity verification, compatibility enforcement, user permission prompts, a constrained extension API, transactional activation/rollback, and community moderation are still required before remote features can be distributed. The current unit tests cover catalog ID uniqueness and install allowlist policy. The latest CI attempt found a compile error in the diagnostics page; its two background-color references have now been corrected, and a fresh CI run must pass before this update is considered validated.


### Network Diagnostics
This installable page validates a hostname/IP address and TCP port, then performs a single socket connection with a 3-second timeout on a background thread. It sends no Minecraft protocol payload, stores no network history, and changes no device network settings. A successful result means only that TCP accepted a connection at that time—not that the Minecraft server is compatible or playable.
