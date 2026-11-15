# Flame Launcher

Flame Launcher is an **Android-first launcher for Minecraft: Java Edition**, owned and maintained by **MFLegend**.

The intended product is an Android phone/tablet app with a touch-friendly interface. The repository currently contains an earlier Tauri 2 + Rust + React desktop-oriented implementation. That code is a source of reusable launcher logic, **not an Android-ready release**. A native Android application shell and initial SAF folder-permission flow are now present. The Android-compatible Java runtime, graphics/input integration, and a verified installable release still need to be implemented.

## Android product direction

- Android phones and tablets are the primary target; this is not a Windows, macOS, or Linux desktop launcher.
- Touch-first navigation, readable mobile layouts, and support for optional external keyboards and mice.
- A complete end-user game-file manager and launcher-engine integration. Kotlin SAF storage primitives support listing, streaming reads/writes, creating, and deleting documents, and the Library tab now provides basic read-only folder browsing. Game installation and full file-management workflows are not connected yet.
- A supported Android Java runtime and native graphics/input integration are required to run Minecraft: Java Edition. Android's built-in ART runtime alone is not a drop-in replacement for a desktop Java runtime.
- Account authentication must use a secure, supported Microsoft/Xbox/Minecraft sign-in flow before online play is enabled.

## Reusable work already present in the repository

- Mojang version manifest browsing and version selection.
- Vanilla metadata, client/library/asset download pipeline with SHA-1 verification.
- Fabric installer integration and an initial Forge installer workflow.
- Version inheritance and launch-argument resolution.
- Installation profiles and isolated game directories.
- Local mod/resource-pack/world/skin management logic.
- Preferences, Java version parsing, and runtime validation helpers.

These features were implemented in the previous desktop-oriented app. Each native filesystem, process, and runtime integration must be reviewed and adapted before it can be considered Android-compatible.

## Android migration status

The Android port is **not yet complete**. In particular, this repository does not yet provide:

- A verified successful Gradle build or installable APK (the Android Gradle module is present, but build verification has not yet been completed).
- Android-compatible JVM/runtime distribution and lifecycle management.
- OpenGL/Vulkan renderer integration, native library ABI packaging, or touch-to-game input bridge.
- SAF-backed game installation, file management, recursive scanning, and launcher-engine integration; the current Library tab only browses folders and files.
- Microsoft account, Xbox Live/XSTS, or Minecraft Services authentication.
- Verified gameplay on physical Android devices.

See [docs/ANDROID_PORT_PLAN.md](docs/ANDROID_PORT_PLAN.md) for the implementation sequence and technical constraints.

## Development of the existing desktop prototype

The current prototype's web frontend can still be inspected with Node.js 20+:

```bash
npm install
npm run dev
```

The Tauri desktop build commands describe the legacy prototype and do **not** produce the Android app. Do not publish the desktop build as Flame Launcher's Android release.

## Ownership

**Product:** Flame Launcher  
**Owner / maintainer:** MFLegend
