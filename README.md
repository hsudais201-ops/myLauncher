# myLauncher

myLauncher is being rebuilt as a Minecraft: Java Edition launcher for Android. Product-owned core code is kept in [`launcher/`](launcher/) instead of being mixed with CI scripts or generated upstream files.

## Development status

The current milestone is a tested Java core foundation for Minecraft version cataloging, local profile-name validation, and conservative memory defaults. This **is not yet a playable Android launcher**: the Android shell, official manifest JSON adapter, game/library/asset download flow, Microsoft account flow, native graphics/runtime integration, and game launch pipeline still need to be implemented and validated.

The previous Amethyst-based build/customization work is preserved on the `backup/pre-clear-2026-10-16` branch. It is intentionally not overwritten while the new code structure is developed.

## Run core tests

Requires a JDK. Run:

```sh
./launcher/test.sh
```

The test script compiles the core source with warnings treated as errors and runs regression tests without requiring Gradle, Android SDK access, or third-party dependencies.

## Planned implementation order

1. Core models and regression tests.
2. Official Minecraft version-manifest download and JSON parsing.
3. Per-version metadata, library/asset resolution, and download integrity checks.
4. Android UI and persistent settings.
5. Runtime/native setup and launch argument construction.
6. Authentication integration, package/build verification, and real-device testing.

A completed unit-test run is not proof of a valid APK or a successful in-game launch. Those checks will be added at the relevant milestones.
