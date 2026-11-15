# Flame Launcher — Android Port Plan

**Product:** Flame Launcher  
**Owner:** MFLegend  
**Primary platform:** Android phones and tablets

This document is the source of truth for the Android migration. The existing Tauri desktop application is a prototype and must not be described as a working Android launcher.

## Current implementation snapshot

- Native Kotlin/Jetpack Compose Android application module and branded navigation shell are committed.
- Settings can open the Android SAF tree picker, request persistable read/write URI access, and save the selected tree URI in app preferences.
- The saved URI is not a filesystem path. Actual document traversal, file reads/writes, imports, downloads, and Rust-engine integration remain unimplemented.
- Gradle build, debug APK generation, and physical-device installation have not yet been verified.

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
- Treat this as a critical feasibility gate: version installation alone does not make Minecraft playable on Android.

## 2. Migration phases

### Phase A — Android foundation
- Add a Gradle Android application module and wrapper.
- Set package ID, minimum/target SDK, arm64 ABI policy, app icon, permissions, and release signing configuration.
- Create a native Android shell with Flame Launcher branding and mobile navigation.
- Add CI that builds a debug APK and reports the artifact; do not call the app playable yet.

### Phase B — Storage and launcher engine
- [x] Implement initial SAF game-directory selection and persist URI permissions in the Android UI.
- [ ] Build a SAF-aware storage abstraction for listing, reading, writing, creating, and deleting documents; never pass a tree URI to desktop `PathBuf` logic as if it were a normal path.
- Refactor Rust filesystem operations behind interfaces so Android document URIs are not treated as ordinary desktop paths.
- Port metadata/version browsing and verified downloads.
- Add cancellation, progress reporting, retry handling, and low-storage checks.

### Phase C — Runtime feasibility and gameplay
- Select and document the Android JVM/runtime distribution and its license.
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
