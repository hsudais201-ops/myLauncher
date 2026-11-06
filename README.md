# Flame Launcher 🔥

Flame Launcher is an Android-first project targeting **Minecraft Java Edition** on mobile devices. It is built with Kotlin and Jetpack Compose and is developed in small, verifiable milestones.

## Current implementation

### Android application foundation
- Native Android application with a Flame-branded Compose interface.
- Main navigation destinations for Home, Play Minecraft, Minecraft Content, Feature Store, Social Hub, ServerState, Flame AI, and Settings.
- Minecraft Content is separate from Feature Store.

### Minecraft content browser
- Modrinth catalog search and compatible-version lookup.
- CurseForge catalog search and version lookup using a user-supplied API key.
- Content categories include mods, modpacks, resource packs, shaders, and datapacks where supported by the provider.
- Local app-private Minecraft content profiles with Minecraft version and loader selection.
- Required-dependency resolution, compatibility planning, staged downloads, integrity checks when provider hashes are available, transactional installation, managed-file manifests, and guarded uninstall.
- Modpack archive and manifest processing, including optional-file selection and exclusion of server-only overrides.
- Unit tests cover provider behavior, dependency resolution, install planning, transactions, manifests, profile storage, and modpack processing.

**CurseForge credential note:** the current client asks the user for their own API key and holds it in Compose memory for the active app session. There is no backend proxy or server-side secret management in this repository yet. Do not commit an API key or describe this client-side arrangement as backend-protected.

**Profile/runtime note:** content profiles are app-private content targets. They are not yet complete playable Minecraft installations.

## Not implemented yet

- Minecraft Java runtime integration, asset/library downloads, and game launching.
- Microsoft account authentication and full offline account/profile management.
- Production backend proxy for CurseForge API credentials.
- Cloud Feature Store installation and its service.
- Social Hub services, live ServerState services, and Flame AI crash repair.

The interface should clearly label these as planned until their implementations and tests are complete.

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

A failed check blocks the next milestone. Do not mark a build or test as passed until its actual result has been inspected.

## Planned roadmap

1. Complete the Android shell and navigation foundation.
2. Harden the Modrinth and CurseForge content browser, including a backend credential proxy.
3. Implement Minecraft version metadata, libraries, assets, and runtime installation.
4. Implement Microsoft authentication and compliant account handling.
5. Build isolated playable instances and a reliable launch pipeline.
6. Add device-aware performance and renderer controls for low-memory devices.
7. Implement the independent cloud Feature Store.
8. Add Social Hub and ServerState services.
9. Add Flame AI diagnostics, reversible repair actions, and crash history.

All work must preserve Minecraft's licensing and authentication requirements, provider API terms, app security, and user control over downloads and repairs.
