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

### Minecraft metadata and runtime foundation
- Mojang version manifest parsing with trusted-host and SHA-1 descriptor validation.
- Bounded metadata downloads with timeouts, redirect rejection, response-size limits, and version JSON SHA-1 verification.
- The Play screen can load official releases asynchronously and inspect verified version metadata, including declared Java major version, libraries, client JAR descriptor, and asset index.
- App-private Java runtime discovery validates each runtime's release metadata, supported architecture, contained JVM entry point, and Java major version; selection requires an exact Java-major and device-ABI match.
- A package catalog parser validates schema, package identity, Java major, normalized ABI, HTTPS download host allowlisting, SHA-256, archive size, and ZIP format before a package can be selected.
- A bounded runtime archive downloader disables redirects, streams to a temporary file, verifies SHA-256 before promotion, and preserves an existing archive when verification fails.
- A package installer accepts a supplied JRE ZIP, verifies its SHA-256, extracts with ZIP-slip and size/entry limits, checks the runtime version and ABI, and replaces an existing package transactionally.
- A runtime install coordinator joins a validated package descriptor to the bounded downloader and transactional installer, checks the downloaded byte count against catalog metadata, reports download/install/completion stages plus byte-level download progress (with an optional total when the server provides it), and always cleans up its temporary archive. Unit tests cover the end-to-end coordinator flow, ABI rejection before network access, and catalog-size mismatch cleanup.
- A MojoLauncher upstream adapter reads its fixed-host runtime signature manifest for Java 17, 21, and 25, selects the published architecture component, and verifies component bytes with the pinned upstream X.509 public key using RSA/SHA-256. The upstream publishes split universal and architecture-specific `.tar.xz` components; its signing certificate is time-limited and must be rotated before expiry.
- A dedicated component downloader validates the exact HTTPS host and component path, rejects redirects, enforces byte/time limits, streams progress, supports cancellation, and verifies the RSA/SHA-256 signature before atomically promoting a component. It also computes SHA-256 for local integrity metadata.
- A package download coordinator obtains both universal and ABI-specific components and promotes the pair as one directory only after both signatures verify; failed or cancelled partial downloads are cleaned up without replacing the previous package.
- A dedicated TAR/XZ extractor uses a bounded XZ decoder, compressed and expanded byte caps, entry-count limits, cancellation checks, path traversal protection, and safe relative symlink handling. It preserves executable bits, accepts safe dot-prefixed TAR paths, and supports universal-then-ABI overlay order. Tests cover regular files, executable permissions, traversal rejection, dot-prefixed paths, and safe/unsafe symlinks.
- A Mojo runtime package installer re-hashes both staged components before extraction, extracts universal then ABI-specific files, validates the release Java major and OS architecture, requires the embedded `libjvm.so`, and transactionally promotes the runtime with backup/restore. Tests cover successful installation, post-download tampering, and wrong Java version rejection.
- The Play runtime panel now exposes Java 17/21/25 install controls, progress/status, cancellation, and automatic runtime rescan after successful installation. The install path uses the pinned certificate and Mojo's fixed-host signed component manifests. This installs files only; it does not yet make the Android JVM executable or launch Minecraft.
- A verified artifact downloader now handles Minecraft client JARs, asset-index JSON, regular libraries, and platform-native library archives from the validated dependency plan. It restricts HTTPS hosts and installation paths, rejects redirects, enforces size limits, verifies SHA-1 and exact byte counts, uses temporary files plus backup/restore promotion, and reuses already verified cached files.
- A sequential artifact installation coordinator installs the client, asset index, libraries, and native archives with aggregate size limits and progress reporting. It deliberately runs one download at a time to limit memory and network pressure on low-resource devices. Asset objects continue through the dedicated asset-index downloader/coordinator.
- A guarded native ZIP/JAR extractor applies Mojang's per-library extraction exclusions, rejects traversal and unsafe paths, blocks duplicate output files, enforces entry/per-file/aggregate expansion limits, supports cancellation, and stages the complete native directory before promotion with backup/restore. A native-install coordinator now connects verified artifact downloads to this extraction stage.

### Minecraft process/session foundation
- A host-agnostic process controller now owns launch-session state transitions (preparing, starting, running, stopping, exited, cancelled, and failed), keeps session snapshots, supports non-blocking graceful stop requests, and allows completed sessions to be released.
- Runtime output and surfaced failure messages redact the launch access token. The controller deliberately does not log or stringify JVM arguments.
- The launch-command builder now supports both process-based runtimes and embedded-JVM runtimes. Embedded commands carry a validated runtime home and contained `libjvm.so`; they cannot be converted into a desktop `ProcessBuilder`.
- An embedded-JVM runtime layout resolver now derives the runtime `lib`, VM-specific, and optional `jli` native-library directories from the contained `libjvm.so` path, orders the extracted Minecraft native directory alongside them, and identifies optional `libawt_headless.so` / `libnio.so` preloads. The bridge contract receives this explicit layout instead of having to reconstruct paths. The resolver only describes paths; it does not mutate Android linker state or load libraries.
- An `EmbeddedJvmProcessHost` adapter validates the runtime paths and converts the launcher's shell-free argument vector into a structured JNI invocation: JVM options, a validated absolute classpath, the main class, and game arguments. It adds the runtime-home and working-directory properties and verifies that the command classpath exactly matches the validated launch plan before forwarding to the narrow `AndroidJvmBridge` contract. The bridge session plugs into the existing process controller without calling `System.exit` on the Android app process.
- Unit tests cover JNI argument parsing, classpath consistency, missing artifacts, invalid main classes, and the structured invocation passed to the bridge.
- A routing host now selects the embedded bridge or an injected process host based on the validated runtime execution kind; the app cannot accidentally route an embedded JVM package through `ProcessBuilder`.
- Unit coverage exercises session startup, graceful stop, terminal state reporting, access-token redaction, embedded command construction, and bridge argument/path forwarding.
- The Android NDK/CMake native library now performs an ELF and Android-linker preflight for an extracted `libjvm.so`: it validates ELF magic, 32/64-bit class, little-endian encoding, shared-object type, and the machine architecture against the running Android ABI before asking the device linker to load the library and checking for `JNI_CreateJavaVM` and `JNI_GetCreatedJavaVMs`. The probe is ABI-packaged for arm64-v8a, armeabi-v7a, x86, and x86_64. The runtime panel exposes this check, and deterministic JVM-path/linker-result tests exercise the Kotlin boundary without loading native code on the unit-test host.
- A separate persistent native loader now validates the same ELF constraints, loads the selected `libjvm.so` with global symbol visibility, verifies both JNI invocation exports, and retains its handle for the Android process lifetime. It is idempotent for the same runtime path and rejects attempts to retain a second JVM path; it deliberately has no unload operation because JVM-owned threads may outlive a game screen.
- The persistent loader is a native integration primitive only. It does not yet load a complete runtime dependency graph, call `JNI_CreateJavaVM`, invoke Minecraft's main class, or provide a safe game-session shutdown. The `AndroidJvmBridge` implementation, JNI thread lifecycle, and device-level compatibility test remain outstanding.

### Rendering foundation
- An Android GLSurfaceView host backed by OpenGL ES 2.0.
- Lifecycle-aware surface pause/resume and on-demand rendering to avoid an unnecessary continuous frame loop.
- A lightweight diagnostic scene verifies shader compilation, program linking, viewport setup, and frame submission.
- This is not yet Minecraft world rendering. A compatible LWJGL-to-Android native bridge, graphics translation layer, and game launch integration are still required.

### Current boundaries
- Content profiles are app-private content targets, not complete playable Minecraft installations.
- Verified version metadata is not equivalent to installed libraries, assets, natives, or client JAR files.
- Java runtime catalog validation, archive downloading, and package installation are separate foundations; none is equivalent to a working JVM launch.
- The OpenGL ES diagnostic renderer is not an implementation of Minecraft's LWJGL renderer.

## Not implemented yet

- End-to-end device testing of authenticated Mojo runtime download, extraction, promotion, cancellation, and replacement rollback.
- Rotating and validating the pinned upstream runtime certificate before its expiry.
- End-to-end device validation of client JAR, libraries, native archives, asset-index and asset-object installation, plus launch-time runtime checks. Native archive extraction is implemented and unit-tested but not yet device-validated.
- Native implementation of `AndroidJvmBridge`, including Android-compatible JVM startup, thread attachment, output forwarding, and safe shutdown. The current NDK library only probes linker compatibility and JNI invocation symbols.
- Minecraft LWJGL rendering compatibility backend and device-level launch validation.
- Wiring the native JVM bridge into the launch UI and end-to-end playable game testing.
- Microsoft account authentication and compliant offline account/profile management.
- Production backend proxy for CurseForge API credentials.
- Cloud Feature Store installation and its service.
- Social Hub services, live ServerState services, and Flame AI crash repair.

The interface should clearly label these as planned until their implementations and tests are complete.

## Requirements

- Android Studio with Android SDK Platform 36 installed.
- JDK 17 or newer for building Flame Launcher.
- Android device or emulator running Android 8.0 (API 26) or newer.

## Build and test

On macOS/Linux:

    ./gradlew testDebugUnitTest assembleDebug

On Windows:

    gradlew.bat testDebugUnitTest assembleDebug

The debug APK is produced at:

    app/build/outputs/apk/debug/app-debug.apk

## Development quality gate

Every milestone follows:

**Build → Inspect → Test → Fix → Retest → Pass → Next step**

A failed check blocks the next milestone. Do not mark a build or test as passed until its actual result has been inspected.

## Roadmap

1. End-to-end device verification of the signed Mojo runtime installation path.
2. End-to-end verification of client JAR, libraries, platform-native extraction, asset index, and assets installation.
3. Implement and package the native `AndroidJvmBridge` for supported ABIs.
4. Integrate the LWJGL/OpenGL compatibility backend, then connect account-aware launch UI, cancellation, logs, and crash recovery.
5. Device-aware performance controls and renderer compatibility profiles.
6. Backend-protected CurseForge credentials.
7. Independent cloud Feature Store, Social Hub, ServerState, and Flame AI services.

All work must preserve Minecraft's licensing and authentication requirements, provider API terms, app security, and user control over downloads and repairs.
