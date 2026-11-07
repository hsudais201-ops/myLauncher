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
- End-to-end device validation of client JAR, libraries, native archives, asset-index and asset-object installation; safe native archive extraction and launch-time runtime checks.
- Android JVM/native bridge and Minecraft LWJGL rendering compatibility.
- End-to-end Minecraft launch arguments, process/session lifecycle, and playable game testing.
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
3. Android JVM bridge and LWJGL/OpenGL compatibility backend.
4. Instance-aware launch arguments, account integration, cancellation, logs, and crash recovery.
5. Device-aware performance controls and renderer compatibility profiles.
6. Backend-protected CurseForge credentials.
7. Independent cloud Feature Store, Social Hub, ServerState, and Flame AI services.

All work must preserve Minecraft's licensing and authentication requirements, provider API terms, app security, and user control over downloads and repairs.
