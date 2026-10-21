# Voidcraft

Voidcraft is a planned Android launcher for Minecraft: Java Edition, with a modern touch-first interface inspired by launchers such as Hyper Launcher.

## Current status

This is the clean bootstrap for the new launcher. It currently contains a Kotlin + Jetpack Compose UI prototype only. It does **not** yet install or launch Minecraft, authenticate Microsoft accounts, provide offline account support, download game assets, or manage mods.

## Project

- **Android app ID:** `com.voidcraft.launcher`
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Minimum Android version:** Android 8.0 (API 26)
- **JDK:** 17
- **Initial version:** 0.1.0

## Build

Open this repository in Android Studio (recent stable version), allow Gradle sync, and run the `app` configuration on an Android device or emulator. Android SDK 35 and JDK 17 are required.

## Planned milestones

1. Establish a reproducible Android build and refine navigation/accessibility.
2. Integrate a maintained Minecraft Java launcher/runtime engine for Android.
3. Add secure Microsoft authentication and clearly separated offline/local profiles where supported.
4. Implement version installation, game launch, runtime/memory settings, and logs.
5. Add mod/profile management, touch controls, skins, file access, and update checks.

The Store and marketplace were intentionally left out of this fresh start. The backup branch `backup/pre-reset-2026-10-21` preserves the previous project for reference.

## Important

Do not commit OAuth client secrets, signing keys, access tokens, or personal account data. Authentication must use the provider's supported flow and secure token storage.
