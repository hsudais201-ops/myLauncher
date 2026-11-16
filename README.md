# Flame Launcher

Flame Launcher is an **Android-first launcher for Minecraft: Java Edition**, owned and maintained by **MFLegend**.

The intended product is an Android phone/tablet app with a touch-friendly interface. The repository currently contains an earlier Tauri 2 + Rust + React desktop-oriented implementation. That code is a source of reusable launcher logic, **not an Android-ready release**. A native Android application shell and initial SAF folder-permission flow are now present. The Android-compatible Java runtime, graphics/input integration, and a verified installable release still need to be implemented.

## Android product direction

- Android phones and tablets are the primary target; this is not a Windows, macOS, or Linux desktop launcher.
- Touch-first navigation, readable mobile layouts, and support for optional external keyboards and mice.
- A complete end-user game-file manager and launcher-engine integration. Kotlin SAF storage primitives support listing, streaming reads/writes, creating, and deleting documents, and the Library tab now provides basic read-only folder browsing plus first-level detection of standard Minecraft directories (`versions`, `assets`, `libraries`, `mods`, `resourcepacks`, and `saves`). The Versions tab fetches Mojang's public version manifest and previews official versions, alongside local version JSON metadata. Users can download an individual version JSON into `versions/<id>/<id>.json` through SAF; the app validates the JSON version ID and verifies the manifest-provided SHA-1 when available. The Versions tab can also stream the vanilla client JAR into `versions/<id>/<id>.jar` through SAF, checking the declared size and SHA-1 without buffering the archive in memory. A separate action downloads unconditional Maven library artifacts into `libraries/<maven-path>`, verifies size and SHA-1, reuses already-verified files, and skips every OS-rule-dependent entry rather than incorrectly treating desktop Linux artifacts as Android-compatible. The Versions catalog can also fetch and SHA-1 verify the official asset index, then stream its content-addressed objects into `assets/objects/<prefix>/<hash>` through SAF. Existing objects are re-hashed and reused. An **Install files** action coordinates metadata, client JAR, unconditional libraries, and assets in sequence, and reports transfer progress with a Cancel action. Downloads are written to temporary SAF documents and promoted to their final names only after size/SHA-1 verification, so interrupted transfers do not leave partial files under final names; completed verified files remain reusable on retry. It is not yet a complete Minecraft installation: OS-rule-dependent libraries/native classifiers are skipped, Android-native dependencies are unresolved, and game launching is not implemented.
- A supported Android Java runtime and native graphics/input integration are required to run Minecraft: Java Edition. Android's built-in ART runtime alone is not a drop-in replacement for a desktop Java runtime. Settings now reports Android API level, ABI support, process bitness, and an app-heap estimate as diagnostics only; this does not mean the Minecraft JVM is installed or launchable. A diagnostic scanner also checks Flame Launcher's private `noBackupFilesDir/java-runtimes/<id>` directory for Java release metadata, ARM64 ABI matching, and `libjvm.so`; this is only a candidate scan, not proof of Android linker compatibility, and it does not install or start the JVM.
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

- A physical-device-verified Android APK. GitHub Actions now successfully builds and uploads a debug APK; it has not yet been installed or tested on physical hardware. [View the successful build](https://github.com/hsudais201-ops/myLauncher/actions/runs/43876638290).
- Android-compatible JVM/runtime distribution and lifecycle management (device capability diagnostics exist, but the game JVM is not installed).
- OpenGL/Vulkan renderer integration, native library ABI packaging, or touch-to-game input bridge.
- Complete SAF-backed game installation, recursive scanning, and launcher-engine integration; the Versions tab now coordinates metadata, the vanilla client JAR, unconditional libraries, and asset objects, but still skips OS-specific native dependencies and does not launch the game.
- Microsoft account, Xbox Live/XSTS, or Minecraft Services authentication.
- Verified gameplay on physical Android devices.

See [docs/ANDROID_PORT_PLAN.md](docs/ANDROID_PORT_PLAN.md) for the implementation sequence and technical constraints, and [docs/ANDROID_RUNTIME_INTEGRATION_ASSESSMENT.md](docs/ANDROID_RUNTIME_INTEGRATION_ASSESSMENT.md) for the current JVM/renderer reference and licensing gates.

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
