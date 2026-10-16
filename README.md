# myLauncher

myLauncher is being rebuilt as a Minecraft: Java Edition launcher for Android. Product-owned code lives in the launcher/ directory, separate from build automation and generated upstream files.

## Development status

The tested Java core now handles master version manifest downloads/JSON decoding, per-version client/library/native metadata, asset-index parsing, and content-addressed resource paths. Verified file downloading is now being added. This is not yet a playable Android launcher: Android UI, account authentication, native runtime integration, launch arguments, and device gameplay tests remain unfinished.

The old Amethyst-based customization/build work is preserved on backup/pre-clear-2026-10-16 and remains untouched while the new code structure is developed.

## Run core tests

Requires a JDK. Run:

    ./launcher/test.sh

This compiles Java source with -Xlint:all -Werror and executes dependency-free regression tests without Gradle or the Android SDK.

## Next milestones

1. Verified downloads to safe cache locations with SHA-1 and size checks.
2. Android UI, persistent settings, and progress/cancellation.
3. Native runtime setup and accurate Minecraft launch-argument construction.
4. Licensed account authentication, APK/package verification, and real-device gameplay tests.

Passing core tests does not prove an APK builds or Minecraft starts successfully.
