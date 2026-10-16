# myLauncher

myLauncher is being rebuilt as a Minecraft: Java Edition launcher for Android. Product-owned core code is kept in [`launcher/`](launcher/) instead of being mixed with CI scripts or generated upstream files.

## Development status

The current milestone is a tested Java core foundation for Mojang manifest download/JSON decoding, per-version client metadata parsing, asset-index/client-download integrity metadata, ordered library rules, native classifiers, local profile-name validation, and conservative memory defaults. This **is not yet a playable Android launcher**: the Android shell, official manifest JSON adapter, game/library/asset download flow, Microsoft account flow, native graphics/runtime integration, and game launch pipeline still need to be implemented and validated.

The previous Amethyst-based build/customization work is preserved on the `backup/pre-clear-2026-10-16` branch. It is intentionally not overwritten while the new code structure is developed.

## Run core tests

Requires a JDK. Run:

```sh
./launcher/test.sh
```

The test script compiles the core source with warnings treated as errors and runs regression tests without requiring Gradle, Android SDK access, or third-party dependencies.

## Planned implementation order

1. Core models, bounded manifest/per-version downloads, JSON parsing, and regression tests.
2. Asset-index parsing, safe cache planning, and SHA-1/size-verified downloads.
3. Android UI and persistent settings.
4. Runtime/native setup and launch argument construction.
5. Authentication integration, package/build verification, and real-device testing.

A completed unit-test run is not proof of a valid APK or a successful in-game launch. Those checks will be added at the relevant milestones.
