# Voidcraft Android Launcher

Voidcraft is a touch-first Android launcher prototype for Minecraft: Java Edition, built with Kotlin and Jetpack Compose.

## Current working pieces

- **Feature Store:** optional and opt-in; only entries present in the JSON catalog are listed. Built-in tools require an explicit Install action before Open is enabled.
- **Social Hub:** local contacts/notes plus an online staging beta for accounts, friend requests, and direct messages.
- **Advanced Mod Manager:** private local JAR import, validation, organize/enable/disable, export and removal. It does not yet inject mods into a running game.
- **Server State:** save server addresses locally and request live Java/Bedrock status from mcsrvstat.us.
- **Theme Studio:** set an accent color and presets, persisted on device.
- **Cosmetic Store:** local launcher-profile preview effects; these are not Minecraft skins or multiplayer cosmetics.
- **Versions:** reads Mojang's official version manifest and lets users save a preferred version.
- **Settings tools:** local profile-name preference, device memory advisor, saved touch-control preferences, private file library, skin PNG validation/preview, and a GitHub release checker.

## Build

- Android application ID: `com.voidcraft.launcher`
- Kotlin + Jetpack Compose / Material 3
- Minimum Android: API 26
- JDK: 17
- Gradle: 8.9
- Compile SDK: 35

Open the project in Android Studio or run `gradle assembleDebug` with JDK 17 and Android SDK 35 installed. The GitHub Actions workflow builds a debug APK and uploads it as the `voidcraft-debug-apk` artifact.

## Important limitations

This is not yet a complete Minecraft launcher. The project does not currently integrate a maintained Minecraft Java runtime/bootstrapper, download/install Minecraft libraries and assets, install selected versions, authenticate Microsoft accounts, or launch the game. The Play button therefore reports that launch integration is unavailable. Version selection, offline profile name, memory preferences, touch preferences, files, and skin preview are local utility features; they do not imply that a game is playable.

The Social Hub backend is a staging beta without email verification, password recovery, push notifications, or end-to-end encryption. Use a unique test password, never reuse a real account password, and do not treat this beta as production-secure.

Do not commit OAuth secrets, signing keys, access tokens, or personal account data.
