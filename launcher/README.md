# Isolated launcher core

This directory contains the product-owned launcher core, separated from CI automation, the core-development shell, and the upstream Amethyst engine used for the actual Android game-launch experience.

## Implemented and tested

- Official Minecraft version-manifest and version-details mapping with strict input validation.
- Safe, bounded HTTPS metadata and file transfers; verified cache reuse; exact size/SHA-1 checking; progress and cancellation.
- Deterministic client/library/asset installation plans, asset-index verification and legacy virtual/resources materialization.
- Rule-based modern game/JVM argument parsing, legacy `minecraftArguments` tokenization, and argv building without shell concatenation.
- Verified classpath resolution and launch preflight; runtime-major probe and private-runtime lookup; launch coordinator validates install paths/index/classpath/runtime, then supervises a Java child process.
- Explicit-target native classifier planning; checksum-verified native downloads; cancellation-aware single/multi-archive extraction with exclusions, traversal/symlink rejection, size caps, duplicate-file rejection and staged publication.
- A dependency-free core regression suite; an earlier baseline passed 371 assertions under JDK 21. This review branch adds new 26.1+ metadata, native-classifier, logging-argument, symlink-path, and large-progress regressions; the latest branch still needs a fresh CI run.

## Run tests

From repository root, with a JDK:

    ./launcher/test.sh

## Android shell

`android-app/` is a small development shell for testing metadata/file-preparation UI and runtime discovery. It is not the real playable launcher and does not replace Amethyst's runtime/renderer. Build it separately:

    cd launcher/android-app
    gradle :app:assembleDebug

The actual Android launcher is built by `.github/workflows/build.yml`, which fetches a pinned Amethyst-Android commit and runs the customizer from `.github/scripts/`. This isolated core is not yet connected to Amethyst's production launch screen. Keep that boundary explicit until a tested integration is implemented.
