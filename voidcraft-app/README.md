# VoidCraft standalone Android shell

This directory is the first independently authored Android app module for the VoidCraft codebase migration.

## Build and tests

With JDK 17 and Android SDK platform 35 installed:

```sh
gradle --no-daemon testDebugUnitTest assembleDebug
```

The generated debug APK is under `app/build/outputs/apk/debug/`. The combined unit-test and APK-build step passed in [GitHub Actions run #39359802263](https://github.com/hsudais201-ops/myLauncher/actions/runs/39359802263).

## Current implementation

- Kotlin Android app using platform Views; this module does not import Amethyst Activity/UI implementation files.
- Minimum Android API 28 (Android 9).
- Dashboard with a working link to the Instances screen.
- Instance profile metadata can be created, viewed, refreshed, and removed from the catalog.
- Catalog data stays in the new app's private files directory when wired through `Context.filesDir`. Writes stage to a temporary file and use atomic replacement where supported.
- Removing a profile removes only its metadata record. It never deletes game directories, worlds, saves, mods, or other files.

## Important limitations

- Profiles currently describe a name, Minecraft version, loader ID, and creation time. They do **not** install Minecraft, validate version manifests, or launch a game.
- Microsoft/Xbox/Minecraft authentication, official version downloads, Java/native runtime integration, rendering/input, feature-store installation, modpack import/export, and the touch-control designer are not connected.
- There is no discovery or migration of data from the existing launcher. This module does not read or modify existing launcher data.
- No physical-device test is claimed. A successful compile/unit-test run does not prove Android device compatibility or Minecraft launch capability.
- The old Amethyst-based build remains unchanged while the replacement is developed.

See [ARCHITECTURE.md](ARCHITECTURE.md), [DATA_MIGRATION.md](DATA_MIGRATION.md), and [TESTING.md](TESTING.md). The repository-level [preliminary source and license audit](../VOIDCRAFT_SOURCE_AND_LICENSE_AUDIT.md) describes why the current whole launcher cannot yet be called copyright-free.
