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
- An optional **Server Status** feature that can be installed/uninstalled from the category-free Feature Store and opens on its own screen.
- Live Minecraft: Java Edition status queries inside the installed feature, including online/reachable state, MOTD, player counts, reported version, and server-provided favicon when available.
- Server requests run off the UI thread with bounded connect/read timeouts and capped status-packet size.
- API 28 minimum Android version.
- Play action intentionally disabled until a tested runtime integration is available.

## Not implemented yet

This is a foundation, not a playable Minecraft launcher. It does not yet contain or integrate a Java runtime, a game downloader, renderer/native libraries, loader installers, account authentication, world/profile management, or a remote Feature Store catalog/community upload service.

The optional Server Status feature appears in the category-free Feature Store. Installing it enables its own screen to query saved Minecraft: Java Edition servers using a hostname/IP and optional port (default 25565). Successful responses can show online status, MOTD, online/max player counts, reported server version, and the server-provided PNG favicon. Queries are read-only; they do not log in to or join the server. Uninstalling the feature keeps saved server names and addresses. The protocol client caps packets at 1 MiB and uses 3-second connect/read timeouts. “Offline / unreachable” means the query did not complete successfully and is not definitive proof that a server process is stopped. Bedrock status and automatic DNS SRV record discovery are not implemented.

## Clean-room boundary

Do not copy or mechanically translate other launcher source files into this subtree. Before introducing external components, document the source, exact version, license, required notices, and why the dependency is needed. The existing root build remains a separate legacy build while this independent implementation is developed and verified.

See [DEPENDENCY_INVENTORY.md](DEPENDENCY_INVENTORY.md) for the audited build/runtime dependency surface. See [../CLEAN_ROOM_POLICY.md](../CLEAN_ROOM_POLICY.md).
