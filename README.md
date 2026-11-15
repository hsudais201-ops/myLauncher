# Flame Launcher

Flame Launcher is an **Android-first launcher for Minecraft: Java Edition**, owned and maintained by **MFLegend**.

The intended product is an Android phone/tablet app with a touch-friendly interface. The repository currently contains an earlier Tauri 2 + Rust + React desktop-oriented implementation. That code is a source of reusable launcher logic, **not an Android-ready release**. The Android application shell, Android-compatible Java runtime, graphics/input integration, and Android packaging still need to be implemented.

## Android product direction

- Android phones and tablets are the primary target; this is not a Windows, macOS, or Linux desktop launcher.
- Touch-first navigation, readable mobile layouts, and support for optional external keyboards and mice.
- Android storage access using the Storage Access Framework and scoped-storage-compatible APIs.
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

- A buildable Android application module or installable APK.
- Android-compatible JVM/runtime distribution and lifecycle management.
- OpenGL/Vulkan renderer integration, native library ABI packaging, or touch-to-game input bridge.
- Android storage permission/document-picker integration for game files.
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
