# myLauncher

myLauncher is being rebuilt as a Minecraft: Java Edition launcher for Android. Product-owned code lives in the launcher/ directory, separate from build automation and generated upstream files.

## Development status

The tested Java core now handles the master Minecraft version manifest, per-version metadata (client JAR, asset index, libraries, runtime requirement and platform rules), and asset-index parsing/content-addressed resource paths. This is not yet a playable Android launcher: safe downloads/cache, Android UI, Microsoft account flow, native runtime integration and game launch still need implementation and device testing.

The old Amethyst-based customization/build work is preserved on backup/pre-clear-2026-10-16. That backup remains untouched while the new structure is developed.

## Run core tests

Requires a JDK. Run:

    ./launcher/test.sh

The script compiles Java core source with -Xlint:all -Werror and executes the dependency-free regression suite without Gradle or an Android SDK.

## Next milestones

1. Verified downloads to safe cache locations with SHA-1 and size checks.
2. Android UI, persistent settings and progress/cancellation.
3. Native runtime setup and accurate Minecraft launch-argument construction.
4. Licensed account authentication, Android build/APK validation and real-device gameplay tests.

Passing core tests does not prove an APK builds or Minecraft starts successfully.
