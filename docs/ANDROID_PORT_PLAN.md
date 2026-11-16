# Flame Launcher — Android Port Plan

**Product:** Flame Launcher  
**Owner:** MFLegend  
**Primary platform:** Android phones and tablets

This document is the source of truth for the Android migration. The existing Tauri desktop application is a prototype and must not be described as a working Android launcher.

## Current implementation snapshot

- Native Kotlin/Jetpack Compose Android application module and branded navigation shell are committed.
- Settings can open the Android SAF tree picker, request persistable read/write URI access, and save the selected tree URI in app preferences.
- A Kotlin SAF storage adapter now supports direct-child listing, stream-based reads/writes, directory/file creation, and document deletion while checking that document URIs remain inside the selected tree.
- The Library tab now browses direct child folders and files from the selected SAF tree, loading provider queries off the main thread and allowing folder navigation.
- At the selected folder root, the Library tab now checks for standard Minecraft directories (`versions`, `assets`, `libraries`, `mods`, `resourcepacks`, and `saves`) and reports which are present.
- The Versions tab scans the selected game root's `versions` directory, reads matching `<version-id>.json` files through SAF, and displays parsed version ID, type, release time, main class, inheritance, and asset-index metadata.
- The Versions tab fetches Mojang's public HTTPS version manifest and displays the latest release/snapshot plus a preview of available versions.
- The Versions catalog can download an individual version JSON into `versions/<id>/<id>.json` through SAF, enforce a response-size limit, check the returned JSON ID, and verify the manifest-provided SHA-1 when present. It can also stream the vanilla client JAR directly into `versions/<id>/<id>.jar`, validating the metadata ID, approved HTTPS host, declared byte size, and SHA-1 while avoiding in-memory buffering. A separate library action streams unconditional Maven `downloads.artifact` entries into `libraries/<path>`, validates safe relative paths and approved HTTPS repository hosts, enforces per-file/aggregate limits, verifies declared size and SHA-1, and skips files already verified locally. Entries with OS-specific `rules` and native classifiers are not downloaded yet, because desktop Linux rules do not establish Android/ARM compatibility. The asset action downloads the official asset index into `assets/indexes/<id>.json`, verifies its SHA-1, and streams content-addressed objects into `assets/objects/<prefix>/<hash>` with per-object size/SHA-1 checks and reuse of verified local files. An **Install files** coordinator now sequences version metadata, vanilla client JAR, unconditional library artifacts, and asset index/objects, returning a per-stage summary. Transfer progress is surfaced in the UI and users can cooperatively cancel the operation between read chunks and stages. Downloaders stage files under temporary SAF names and promote them only after checksum/size verification; completed verified files remain reusable after cancellation or a later failure. This is still not a complete install: OS-rule-dependent libraries/native classifiers are skipped, Android native dependency resolution and complete rule evaluation remain unimplemented, and there is no launch integration. File import/export and Rust-engine integration also remain unimplemented.
- A diagnostic-only Android runtime capability inspector now reports Android API level, supported ABIs, 64-bit process state, app heap limit, and processor count in Settings. It identifies whether the current process is suitable for the initial arm64-v8a runtime target; it does not install, load, or start a Minecraft JVM. A second diagnostic component scans only Flame Launcher's private `noBackupFilesDir/java-runtimes/<id>` directory for Java `release` metadata and an ARM64 `libjvm.so`, rejects malformed/out-of-tree entries, and reports compatible bundles in Settings. It does not extract, download, execute, or load a runtime. This keeps native JVM files out of SAF document storage, where native loading cannot be assumed.
- The first Android CI build exposed four Kotlin UTF-8 decoding type errors; those have been corrected. The legacy desktop Rust check also exposed a native-library classifier scope error, which has been corrected. The subsequent full GitHub Actions run passed all three jobs: frontend build, Android debug APK build plus artifact upload, and legacy Rust `cargo check`. [View the successful run](https://github.com/hsudais201-ops/myLauncher/actions/runs/43876638290).
- The debug APK has been built and uploaded by CI, but it has not yet been installed or tested on a physical Android device.

## 1. Target architecture

### Android application layer
- Kotlin + Android Gradle Plugin for the Android app, activity lifecycle, permissions, document picker, notifications, foreground game-session service, and app packaging.
- Compose or a native Android UI for touch-first screens, responsive layouts, accessibility, and Android back navigation.
- SAF document access for user-selected game directories and imports; avoid assuming unrestricted access to shared storage.
- ABI-aware packaging for supported Android devices (initially arm64-v8a).

### Launcher engine
- Reuse Rust for platform-independent work where practical: Mojang metadata parsing, version inheritance, manifest validation, checksum verification, safe path handling, and download orchestration.
- Put Android-specific filesystem, process, and runtime behavior behind platform interfaces. Do not carry desktop assumptions such as arbitrary executable paths, desktop process spawning, or unrestricted filesystem access into Android.
- Expose the reusable engine to Kotlin through a deliberate JNI boundary or a maintained Rust/Kotlin bridge. Keep UI state and Android lifecycle in the Android layer.

### Minecraft Java runtime and graphics
- Bundle or provision a legally distributable Android-compatible OpenJDK runtime for the selected Minecraft versions and device ABI. Android ART is not a substitute for the desktop JVM required by many Minecraft Java builds.
- Integrate a maintained Android Java-game runtime/rendering approach, including OpenGL ES/Vulkan translation as required by the selected runtime.
- Provide native library loading, window/surface lifecycle, audio, touch mapping, keyboard/mouse input, and pause/resume handling.
- Treat this as a critical feasibility gate: version installation alone does not make Minecraft playable on Android. Device ABI diagnostics are not evidence that a compatible game JVM, renderer, or native dependency loader is installed.

## 2. Migration phases

### Phase A — Android foundation
- Add a Gradle Android application module and wrapper.
- Set package ID, minimum/target SDK, arm64 ABI policy, app icon, permissions, and release signing configuration.
- Create a native Android shell with Flame Launcher branding and mobile navigation.
- Add CI that builds a debug APK and reports the artifact; do not call the app playable yet.

### Phase B — Storage and launcher engine
- [x] Implement initial SAF game-directory selection and persist URI permissions in the Android UI.
- [x] Add a SAF-aware Kotlin storage abstraction for listing, reading, writing, creating, and deleting documents.
- [x] Connect the SAF adapter to a basic in-app folder browser with directory navigation.
- [x] Add first-level detection of standard Minecraft game-directory folders.
- [x] Scan installed version folders and display metadata from matching version JSON files.
- [x] Save official version metadata into the selected SAF game directory after validating the version ID and manifest SHA-1 (when supplied).
- [x] Stream and verify the vanilla client JAR into the selected version folder using its official metadata size and SHA-1.
- [ ] Download and verify required libraries, assets, and platform-specific native dependencies.
- [ ] Integrate SAF document operations into complete launcher-engine installation and game-file workflows; never pass a tree URI to desktop `PathBuf` logic as if it were a normal path.
- Refactor Rust filesystem operations behind interfaces so Android document URIs are not treated as ordinary desktop paths.
- Port metadata/version browsing and verified downloads.
- Add cancellation, progress reporting, retry handling, and low-storage checks.

### Phase C — Runtime feasibility and gameplay
- [x] Add diagnostic-only device checks for the initial arm64-v8a target, Android API level, process bitness, and memory budget.
- [ ] Select and document the Android JVM/runtime distribution and its license.
- Integrate graphics translation, native libraries, surface lifecycle, and touch/keyboard input.
- Validate one known compatible Minecraft version on physical arm64 hardware before expanding version support.
- Add memory budgeting, thermal awareness, crash logs, and process/session lifecycle handling.

### Phase D — Mods, accounts, and release readiness
- Port mod/resource-pack/world management through SAF-safe operations.
- Implement Microsoft device authorization, Xbox Live/XSTS, Minecraft Services, and secure token storage before enabling online accounts.
- Test offline and online modes, supported versions, device compatibility, storage edge cases, and upgrade paths.
- Publish APK/AAB only after license review, security review, and physical-device testing.

## 3. Rules for the port

- Never claim Android support, APK availability, or successful gameplay until a real Android build and physical-device test verify it.
- Never run desktop-only `std::process::Command` Java launch code on Android as if it were a complete game runtime.
- Do not request broad storage permissions when SAF can provide the required access.
- Do not embed Microsoft client secrets or store account tokens in plain preferences.
- Keep downloads restricted to trusted official metadata/artifact endpoints and verify hashes where authoritative hashes are provided.
- Preserve the owner attribution as **MFLegend**; the app/product name remains **Flame Launcher**.

## 4. First acceptance criteria

The first Android milestone is complete only when:
1. A debug APK builds from the repository's documented instructions.
2. The APK installs and opens on a physical Android arm64 device.
3. The UI is touch-usable in portrait and landscape.
4. A user can select a game-data location using Android's document picker and the permission survives an app restart.
5. The app clearly labels Minecraft gameplay as unavailable until the compatible runtime and renderer milestone is complete.

A later gameplay milestone must additionally demonstrate a real Minecraft Java session on a physical device, including rendering, input, audio, pause/resume, and clean shutdown.
