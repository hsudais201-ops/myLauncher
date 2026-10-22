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
- **Settings tools:** local profile-name preference, device memory advisor, saved touch-control preferences, private file library, skin PNG validation/preview, GitHub release checker, and a Java Runtime Manager.
- **Render Backend:** an Android OpenGL ES 2.0 surface/shader smoke test accessible from Settings, for checking that the device can create a basic GPU rendering context.

## Build

- Android application ID: `com.voidcraft.launcher`
- Kotlin + Jetpack Compose / Material 3
- Minimum Android: API 26
- JDK: 17
- Gradle: 8.9
- Compile SDK: 35

Open the project in Android Studio or run `gradle assembleDebug` with JDK 17 and Android SDK 35 installed. The GitHub Actions workflow builds a debug APK and uploads it as the `voidcraft-debug-apk` artifact.

## Important limitations

This is not yet a complete Minecraft launcher. The Java Runtime Manager can download Java 8, 17, 21, and 25 Android OpenJDK builds from the upstream [Amethyst OpenJDK build releases](https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases) for supported CPU architectures. It validates each package's exact size and published SHA-256 digest, safely extracts archive entries, and runs `java -version`. Java 25 is not published for 32-bit x86, and should only be chosen when the selected game/mod loader supports it. A JRE is only the Java runtime, not a Minecraft launch engine. The OpenGL ES screen is only a GPU smoke test; it does not yet render Minecraft or provide the game's OpenGL compatibility layer. The project still needs a maintained launch/bootstrap engine and rendering/native libraries, Minecraft library/asset download and version installation, secure Microsoft authentication, and actual game launch. The Play button therefore reports that game launch integration is unavailable. Version selection, offline profile name, memory preferences, touch preferences, files, and skin preview remain local utility features; they do not imply that a game is playable.

The Social Hub backend is a staging beta without email verification, password recovery, push notifications, or end-to-end encryption. Use a unique test password, never reuse a real account password, and do not treat this beta as production-secure.

Do not commit OAuth secrets, signing keys, access tokens, or personal account data.
