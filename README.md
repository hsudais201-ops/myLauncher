# myLauncher

myLauncher is being rebuilt as a Minecraft: Java Edition launcher for Android. Product-owned code lives in the launcher/ directory, separate from build automation and generated upstream files.

## Development status

The project has a tested Java core for official version metadata, asset-index verification and safe SHA-1-verified cached downloads, plus an Android shell that loads the official version list and saves basic settings. The installation planner now builds deterministic client/library/asset download tasks, filters library rules and removes duplicate payloads.

This is not yet a playable Android launcher. The current interface can load version metadata and persist settings, but installation execution, metadata/cache materialization, Android-compatible Java/runtime/native integration, Microsoft account authentication, launch-argument generation and real-device gameplay tests remain unfinished.

The old Amethyst-based customization/build work is preserved on backup/pre-clear-2026-10-16 and remains untouched while the new code structure is developed.

## Run the Java core tests

Requires a JDK:

    ./launcher/test.sh

This compiles Java source with -Xlint:all -Werror and executes dependency-free regression tests without Gradle or the Android SDK.

## Build the Android shell

Install Android SDK platform 35 and build-tools 35.0.0, JDK 17+, and Gradle 8.9. Then run:

    cd launcher/android-app
    gradle :app:assembleDebug

The GitHub Actions workflow builds and verifies the debug APK on pull requests. The APK currently provides a launcher shell, not the Minecraft game engine/runtime.

## Next milestones

1. Connect the download plan to progress-aware installation and persistent cache metadata.
2. Add Android-compatible runtime/native handling and accurate Minecraft launch arguments.
3. Integrate licensed Microsoft authentication and validate game launch on real Android devices.

Passing core tests or producing a debug APK does not prove Minecraft starts successfully.
