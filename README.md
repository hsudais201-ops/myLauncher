# Flame Launcher 🔥

Flame Launcher is an Android-first project aimed at running the **real Minecraft Java Edition** on mobile devices.

This repository is being developed in verified milestones. The first milestone establishes the native Android project, app identity, and navigation foundation.

## Current milestone: Android foundation

Included in this milestone:

- Native Android application using Kotlin and Jetpack Compose.
- Flame-branded dark interface.
- Main navigation drawer with separate destinations for Home, Play Minecraft, Minecraft Content, Feature Store, Social Hub, ServerState, Flame AI, and Settings.
- Separate visual areas for Modrinth/CurseForge content and launcher features.
- Unit tests for the navigation model.
- GitHub Actions build and test workflow.
- Gradle 9.7.0 wrapper.

**Not implemented yet:** Minecraft Java runtime integration, game downloads and launching, Microsoft authentication, offline profile management, Modrinth/CurseForge APIs, cloud Feature Store installation, Social Hub services, live ServerState, or AI crash repair. The current app labels these areas as planned instead of presenting non-working controls as functional.

## Requirements

- Android Studio with Android SDK Platform 36 installed.
- JDK 17 or newer.
- Android device or emulator running Android 8.0 (API 26) or newer.

## Build and test

On macOS/Linux:

```bash
./gradlew testDebugUnitTest assembleDebug
```

On Windows:

```bat
gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is produced at:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Development quality gate

Every milestone follows:

**Build → Inspect → Test → Fix → Retest → Pass → Next step**

A failed check blocks the next milestone. Fixes must be verified before proceeding.

## Planned architecture

1. Android shell and navigation.
2. Minecraft runtime research and integration.
3. Local profiles, offline accounts, and Microsoft authentication.
4. Per-instance game files, version manifests, libraries, assets, and launch pipeline.
5. Device-aware performance and renderer controls.
6. Modrinth and CurseForge content browser as a main navigation destination.
7. Independent cloud Feature Store for official and community launcher features.
8. Social Hub and ServerState services.
9. Flame AI diagnostics, reversible repair actions, and crash history.

All future work must preserve Minecraft's licensing and authentication requirements, provider API terms, app security, and user control over downloads and repairs.
