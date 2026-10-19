# VoidCraft standalone Android shell

This directory is the first independently authored Android app module for the VoidCraft codebase migration.

## Build

From this directory, with JDK 17 and Android SDK platform 35 installed:

```sh
gradle assembleDebug
```

The generated debug APK is under `app/build/outputs/apk/debug/`.

## Scope and limitations

- Kotlin Android app using platform Views; no Amethyst application UI source is imported by this module.
- Minimum Android API 28 (Android 9).
- Dashboard sections are intentionally prototypes. Their buttons report that services are not connected.
- No Minecraft runtime, Microsoft authentication, downloader, feature-store installer, persistent instance repository, or world migration is wired in yet.
- This module does not access or modify the existing launcher data.
- This shell alone cannot launch Minecraft and is not a replacement release.

The existing upstream-based build remains untouched while each service is independently implemented, tested, and integrated behind explicit interfaces.
