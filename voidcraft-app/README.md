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

The independent Android app now has a shared first-party Feature Store catalog with 23 entries and a fail-closed local registry. The original 11 catalog entries are joined by 12 additions: Resource Pack Manager, Resource Pack Compatibility Checker, World Details Viewer, Playtime Tracker, Accessibility Presets, Profile Icon Studio, Configuration Backup, Network Diagnostics, Minecraft Release Watch, Controller Presets, Resource Usage History, and Instance Notes. Most catalog entries are still planned rather than shipped. Four built-in add-ons are available: Theme Studio, Startup Diagnostics, Instance Notes, and Network Diagnostics. Custom Feature Studio is a separate local blueprint editor, not an installable catalog plug-in. The Feature Store screen reads from that catalog, displays declared capabilities, and explains why a feature is unavailable rather than simulating a successful install. Unknown IDs, traversal-like IDs, planned features, and community submissions are not installable. The registry never loads or executes downloaded add-on code.

This is deliberately a security foundation, not a finished online store. The four installable built-ins are Theme Studio, Startup Diagnostics, Instance Notes, and Network Diagnostics. Theme Studio offers five saved launcher accent palettes and can reset to Void Violet; it changes launcher accent UI only, not Minecraft graphics or FPS. Startup Diagnostics reads device/app memory and storage information, reports runtime integration status, and can copy a diagnostic report. Instance Notes keeps up to 5,000 characters of private note text per profile, supports save/edit/delete, and does not modify game files or world saves. Network Diagnostics performs one user-triggered TCP connect to the supplied host and port, with a 3-second timeout on a worker thread; a reachable port does not establish Minecraft protocol status. Custom Feature Studio stores up to 100 local, editable design blueprints, with field-size limits and JSON share/import. Imported blueprints must match the versioned VoidCraft JSON envelope, are limited to 12 KB, are previewed before import, and are copied to a new local ID. IDs/status from external JSON are ignored. Blueprints are never executed or installed, and the studio does not grant game-file or network access. Signed manifests, package integrity verification, compatibility enforcement, user permission prompts, a constrained extension API, transactional activation/rollback, and community moderation are still required before remote plug-ins can be distributed. The unit tests cover catalog uniqueness, all 23 catalog entries, and the strict allowlist for four implemented built-ins. The current Theme Studio, Feature Studio, and store build passed unit tests and debug APK compilation in [GitHub Actions run #39542282718](https://github.com/hsudais201-ops/myLauncher/actions/runs/39542282718).


### Network Diagnostics
This installable page validates a hostname/IP address and TCP port, then performs a single socket connection with a 3-second timeout on a background thread. It sends no Minecraft protocol payload, stores no network history, and changes no device network settings. A successful result means only that TCP accepted a connection at that time—not that the Minecraft server is compatible or playable.

### Theme Studio
Theme Studio offers five built-in accent palettes and a reset-to-default option. The selected palette is stored in launcher preferences and is applied when the home screen, Settings, or Feature Store is reopened. It does not change renderer settings, Minecraft graphics, or the FPS limit.

### Custom Feature Studio
The no-code studio saves up to 100 local design blueprints with bounded field lengths. Users can edit or delete drafts, share a versioned JSON envelope, or paste an exported envelope into Import. Import validates the format/version and size, checks field limits, previews the blueprint, and creates a new local ID while ignoring external IDs and status fields. A blueprint is descriptive data only: it cannot be executed, install itself, or access game files. Online submission and publishing are not enabled; community uploads stay review-required and non-installable until a secured backend and review process exist.

## Social Hub

The Android Social Hub provides a community landing page, the official VoidCraft Discord invite, and clearly separated Friends & Requests, Private Messages, and Player Profile entry points. Friends and direct messages are not live features in this build: they are explicitly marked unavailable rather than populated with fake online users or simulated conversations. Real social features require authenticated identity, server-side authorization, safe message storage, block/report controls, rate limits, and privacy/deletion handling. Opening the community links launches the supplied Discord invite; the launcher does not claim to provide in-app chat.
