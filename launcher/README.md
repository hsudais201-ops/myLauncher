# Isolated launcher core

This directory contains the product-owned launcher core, separated from CI automation, the core-development shell, and the upstream Amethyst engine used for the actual Android game-launch experience.

## Implemented and tested

- Official Minecraft version-manifest and version-details mapping with strict input validation.
- Safe, bounded HTTPS metadata and file transfers; verified cache reuse; exact size/SHA-1 checking; progress and cancellation.
- Deterministic client/library/asset installation plans, asset-index verification and legacy virtual/resources materialization.
- Rule-based modern game/JVM argument parsing, legacy `minecraftArguments` tokenization, and argv building without shell concatenation.
- Verified classpath resolution and launch preflight; runtime-major probe and private-runtime lookup; launch coordinator validates install paths/index/classpath/runtime, then supervises a Java child process.
- App-private multi-runtime discovery for conventional Java 8/17/21/25 slots and additional runtime IDs, actual `java -version` probing, visible missing/broken slots, and exact-major selection without silently substituting an incompatible runtime.
- Explicit-target native classifier planning; checksum-verified native downloads; cancellation-aware single/multi-archive extraction with exclusions, traversal/symlink rejection, size caps, duplicate-file rejection and staged publication.
- A dependency-free core regression suite; an earlier baseline passed 371 assertions under JDK 21. This review branch adds new 26.1+ metadata, native-classifier, logging-argument, symlink-path, and large-progress regressions; the latest branch still needs a fresh CI run.

## Run tests

From repository root, with a JDK:

    ./launcher/test.sh

## Android shell

`android-app/` is a small development shell for testing metadata/file-preparation UI and runtime discovery. It is not the real playable launcher and does not replace Amethyst's runtime/renderer. Build it separately:

    cd launcher/android-app
    gradle :app:assembleDebug

The actual playable Android launcher is built by `.github/workflows/build.yml`, which fetches a pinned Amethyst-Android commit and runs the customizer from `.github/scripts/`.

## Unified playable-launcher home

The customizer connects the Voidcraft shortcuts directly to screens in the playable Amethyst engine:

- **Accounts** opens the existing authentication chooser for Microsoft sign-in or local/offline username sign-in.
- **Create Instance / Profile** opens the engine's profile-type workflow. The existing Minecraft profile/version selector and edit-profile action remain available on the home screen for selecting and editing installed profiles.
- **Java & Runtime** opens the engine's Java/multi-runtime preferences. This keeps Android-compatible runtime configuration with the engine that actually launches Minecraft.
- **Launcher Settings** opens the existing launcher preferences; **Graphics & Renderer**, **Controls & Touch Settings**, and **Customize Controls** open the real engine screens.
- **Performance Overlay** is optional and disabled by default; in-game it can be toggled and dragged, with FPS and process-memory readouts independently configurable.

The separate `launcher/android-app/` development shell and its isolated runtime catalog are still test tools, not the playable UI. The isolated catalog probes ordinary Java executable folders but does not download or provision Android JRE archives and does not replace Amethyst's Android-native runtime installer. The playable APK uses the engine-managed runtime, renderer, input, account, and profile flows.
