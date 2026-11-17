# Flame Launcher

Flame Launcher is an **Android-first launcher for Minecraft: Java Edition**, owned and maintained by **MFLegend**.

The intended product is an Android phone/tablet app with a touch-friendly interface. The repository currently contains an earlier Tauri 2 + Rust + React desktop-oriented implementation. That code is a source of reusable launcher logic, **not an Android-ready release**. A native Android application shell and initial SAF folder-permission flow are now present. A Java runtime folder can now be imported into private storage, but Android-compatible JVM loading/execution, graphics/input integration, and a verified playable release still need to be implemented.

## Android product direction

- Android phones and tablets are the primary target; this is not a Windows, macOS, or Linux desktop launcher.
- Touch-first navigation, readable mobile layouts, and support for optional external keyboards and mice.
- A complete end-user game-file manager and launcher-engine integration. Kotlin SAF storage primitives support listing, streaming reads/writes, creating, and deleting documents, and the Library tab now provides basic read-only folder browsing plus first-level detection of standard Minecraft directories (`versions`, `assets`, `libraries`, `mods`, `resourcepacks`, and `saves`). The Versions tab fetches Mojang's public version manifest and previews official versions, alongside local version JSON metadata. Users can download an individual version JSON into `versions/<id>/<id>.json` through SAF; the app validates the JSON version ID and verifies the manifest-provided SHA-1 when available. The Versions tab can also stream the vanilla client JAR into `versions/<id>/<id>.jar` through SAF, checking the declared size and SHA-1 without buffering the archive in memory. A separate action downloads unconditional Maven library artifacts into `libraries/<maven-path>`, verifies size and SHA-1, reuses already-verified files, and skips every OS-rule-dependent entry rather than incorrectly treating desktop Linux artifacts as Android-compatible. The Versions catalog can also fetch and SHA-1 verify the official asset index, then stream its content-addressed objects into `assets/objects/<prefix>/<hash>` through SAF. For indexes that declare `virtual` or `map_to_resources`, it additionally creates verified copies under `assets/virtual/<index-id>/...` or `resources/...`; repeated hashes are downloaded once but mapped to every logical asset path. Existing objects and verified mapped files are re-hashed and reused. An **Install files** action coordinates metadata, client JAR, unconditional libraries, and assets in sequence, and reports transfer progress with a Cancel action. Downloads are written to temporary SAF documents and promoted to their final names only after size/SHA-1 verification, so interrupted transfers do not leave partial files under final names; completed verified files remain reusable on retry. It is not yet a complete Minecraft installation: OS-rule-dependent libraries/native classifiers are skipped, Android-native dependencies are unresolved, and game launching is not implemented.
- A supported Android Java runtime and native graphics/input integration are required to run Minecraft: Java Edition. Android's built-in ART runtime alone is not a drop-in replacement for a desktop Java runtime. Settings now reports Android API level, ABI support, process bitness, and an app-heap estimate as diagnostics only; this does not mean the Minecraft JVM is installed or launchable. Settings can import an already-extracted Java runtime folder into Flame Launcher's private `noBackupFilesDir/java-runtimes/<id>` directory. The importer limits file count/size/depth, validates every document name, checks `release` metadata and the AArch64 ELF64 `libjvm.so` header, and promotes the verified staging copy to its private runtime directory. The scanner checks the installed runtime's release metadata, ABI, paths, and ELF header; neither import nor scanning loads or executes it, and neither proves Android linker/native compatibility.
- Account authentication must use a secure, supported Microsoft/Xbox/Minecraft sign-in flow before online play is enabled. See [Microsoft account setup requirements](docs/MICROSOFT_ACCOUNT_AUTH_SETUP.md); the project-owned client ID and service access are still outstanding.

## Games catalog

The Android **Games** tab separates the two experiences:

- **Minecraft: Java Edition** is the main Flame Launcher integration. Flame Launcher can install currently supported vanilla files and prepare version launch metadata/modern arguments. The Games tab also offers an optional handoff to the separately installed [Amethyst Android launcher](https://github.com/AngelAuraMC/Amethyst-Android); the button opens Amethyst when installed, otherwise it opens the official prebuilt-build download. Amethyst uses its own game files and sign-in. Flame Launcher still cannot start Minecraft by itself until its own Android JVM, native graphics/input/audio stack, lifecycle handling, and physical-device gameplay tests are complete. No Amethyst code or runtime binaries are bundled; see the dependency/license audit before any future reuse.
- **Rust Mobile** is a separate mobile game, not a Minecraft edition. The Games tab opens the installed Rust Mobile Android app when available, or sends the user to the [publisher's official Rust Mobile site](https://www.rustmobile.com/en/m/) when it is not installed. The publisher's current site advertises its second beta and an APK download. Flame Launcher does not host or redistribute the proprietary APK; beta access, account, device, and region eligibility remain controlled by the publisher.

Flame Launcher does not include either game's paid content or bypass publisher account, purchase, region, or device requirements.

## Reusable work already present in the repository

- Mojang version manifest browsing and version selection.
- Minecraft launch-metadata validation plus modern game/JVM argument resolution (ordered OS/feature rules and placeholder substitution) and legacy quoted game-argument tokenization. Android is not treated as desktop Linux. This prepares command arguments but does not yet build the full classpath/native environment or start a JVM.
- The Play readiness gate also requires Microsoft/Xbox authentication, verified Minecraft entitlement, native/graphics/input/audio/lifecycle readiness, and a successful physical-device game test. These requirements default to disabled.
- Vanilla metadata, client/library/asset download pipeline with SHA-1 verification.
- Fabric installer integration and an initial Forge installer workflow.
- Version inheritance and launch-argument resolution.
- Installation profiles and isolated game directories.
- Local mod/resource-pack/world/skin management logic.
- Preferences, Java version parsing, and runtime validation helpers.

These features were implemented in the previous desktop-oriented app. Each native filesystem, process, and runtime integration must be reviewed and adapted before it can be considered Android-compatible.

## Android migration status

The Android port is **not yet complete**. In particular, this repository does not yet provide:

- A physical-device-verified Android APK. GitHub Actions now successfully builds and uploads a debug APK; it has not yet been installed or tested on physical hardware. [View the successful build](https://github.com/hsudais201-ops/myLauncher/actions/runs/43882518888).
- Android Java runtime execution and lifecycle management (a user-supplied ARM64 runtime can be imported and inspected, but no supported JVM is loaded or started by Flame Launcher).
- OpenGL/Vulkan renderer integration, native library ABI packaging, or touch-to-game input bridge.
- Complete SAF-backed game installation, recursive scanning, and launcher-engine integration; the Versions tab now coordinates metadata, the vanilla client JAR, unconditional libraries, and asset objects, but still skips OS-specific native dependencies and does not launch the game.
- Microsoft account, Xbox Live/XSTS, or Minecraft Services authentication (registration and integration requirements are documented, but sign-in is not implemented).
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
