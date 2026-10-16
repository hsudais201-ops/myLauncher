# myLauncher

myLauncher is being rebuilt as a Minecraft: Java Edition launcher for Android. Product-owned code lives in the launcher/ directory, separate from build automation and generated upstream files.

## Development status

The project now has a tested Java core for master-version manifest downloads, per-version client/library/native metadata, asset-index parsing and integrity checking, plus a checksum-verified file-cache downloader. A first native Android app shell is being added for version selection, a local profile-name field, and a conservative heap-memory setting.

This is not yet a playable Android launcher. The current interface can load Mojang's official version list and save basic settings, but the verified download components still need to be orchestrated into a complete installer; Microsoft account authentication, Android-compatible Java/runtime/native integration, launch-argument generation and actual in-game testing remain unfinished.

The old Amethyst-based customization/build work is preserved on backup/pre-clear-2026-10-16 and remains untouched while the new code structure is developed.

## Run the Java core tests

Requires a JDK:

    ./launcher/test.sh

This compiles Java source with -Xlint:all -Werror and executes dependency-free regression tests without Gradle or the Android SDK.

## Build the Android shell

Install Android SDK platform 35 and build-tools 35.0.0, JDK 17+, and Gradle 8.9. Then run:

    cd launcher/android-app
    gradle :app:assembleDebug

The GitHub Actions workflow builds and verifies the debug APK on pull requests. The APK currently provides a working launcher shell, not the Minecraft game engine/runtime.

## Implementation order

1. Finish installation orchestration using verified client/library/asset downloads.
2. Add Android-compatible runtime/native handling and accurate Minecraft launch arguments.
3. Integrate licensed Microsoft authentication, then validate installation/launch behavior on real Android devices.

Passing a core test or producing a debug APK does not prove Minecraft starts successfully.
