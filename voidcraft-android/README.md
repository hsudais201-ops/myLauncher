# VoidCraft Launcher — clean-room foundation

This is an independent Android application subtree built from first-party source. It has its own Gradle project, Java package (`com.voidcraft.launcher`), Android manifest, dashboard, navigation, local settings, and build workflow. It does not pull source from another Minecraft launcher.

## Build

Requirements: JDK 17, Android SDK Platform 35, and Gradle 8.9.

From the repository root:

```sh
gradle -p voidcraft-android :app:assembleDebug
```

Debug APK output:

```
voidcraft-android/app/build/outputs/apk/debug/app-debug.apk
```

A dedicated GitHub Actions workflow builds this subtree and runs boundary checks without fetching the upstream launcher repository.

## What currently works

- Original dashboard and six-page navigation shell.
- Installations, Downloads, Feature Store, Servers, and Settings navigation.
- Locally persisted low-memory and reduced-motion preferences.
- Locally saved server entries with add, edit, remove, and refresh-list actions.
- Saved server names and addresses persist across app restarts.
- API 28 minimum Android version.
- Play action intentionally disabled until a tested runtime integration is available.

## Not implemented yet

This is a foundation, not a playable Minecraft launcher. It does not yet contain or integrate a Java runtime, a game downloader, renderer/native libraries, loader installers, account authentication, world/profile management, or a remote Feature Store catalog. Server management currently stores names and addresses locally; refreshing reloads the local list only. Live online/offline checks, MOTD, player counts, versions, and server icons are not implemented. The UI says so rather than displaying fabricated status.

## Clean-room boundary

Do not copy or mechanically translate other launcher source files into this subtree. Before introducing external components, document the source, exact version, license, required notices, and why the dependency is needed. The existing root build remains a separate legacy build while this independent implementation is developed and verified.

See [../CLEAN_ROOM_POLICY.md](../CLEAN_ROOM_POLICY.md).
