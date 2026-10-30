# Hyper Launcher — Android + Mobile UI

A mobile-first launcher prototype for Minecraft Java Edition. The repository contains the browser preview and an initial native Android app shell.

## Current working pieces

- Mobile-first Hyper Launcher home screen and touch-friendly navigation.
- Version selector and memory target controls.
- Browser-local preference persistence where storage is available.
- Native Android Kotlin app shell that loads the UI bundled inside the APK.
- Android-side reader for Mojang's public version manifest, with a UI control to sync official release and snapshot version IDs.
- Manifest size limits, network timeouts, HTTPS host allowlists, SHA-1 validation, and unit tests for parsing/filtering version metadata.
- Per-version metadata resolver that verifies the official metadata SHA-1 and extracts client JAR and asset-index download descriptors.
- App-private file downloader foundation with trusted-host redirect checks, path-traversal protection, exact-size checks, SHA-1 verification, temporary files, and atomic finalization.
- Asset-index parser that validates object hashes and sizes and builds content-addressed asset paths.
- Install planner that includes the asset-index JSON, client JAR, standard Maven libraries, declared native classifier archives, and indexed asset objects with expected sizes and SHA-1 hashes.
- Library and native-classifier resolvers that apply the supported Linux rules, select only explicitly declared architecture classifiers, validate artifact paths, and accept only trusted HTTPS download hosts.
- Native ZIP extraction into a version-specific private directory, with path-traversal checks, cancellation checks, and per-entry/total extraction limits.
- Android install controls connected to the native bridge, with progress reporting and a cancel action. The bridge resolves the selected official version, builds the plan, and runs the cancellable installation service on a worker thread.
- Cancellable installation service that downloads the planned asset index, client JAR, standard libraries, native classifiers, and assets into app-private storage, extracts native archives, and reports download byte/file progress.
- Unit tests for version metadata, asset-index parsing, trusted download URLs, downloader path safety, and cancellation before the first download.
- GitHub Actions workflow to build a debug APK and upload it as an artifact.

## Build the Android preview

Pushes to `main` that change the Android project, pull requests, and manual workflow runs trigger the Android build workflow. When it succeeds, download the **hyper-launcher-debug-apk** artifact from that workflow run on GitHub Actions.

The Android project uses JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, and compile SDK 35. The build has been configured in the repository; a successful CI result has not yet been verified in this conversation.

## Important status — not a finished Minecraft launcher

The Android app is still an early prototype. In the Android app, the install button can resolve an official version and download/verify its asset-index JSON, client JAR, standard libraries, declared Linux native classifier archives, and indexed asset objects into app-private storage. Native archives are extracted into a version-specific natives directory. Download progress and cancellation are supported. Downloads run on a worker thread owned by the activity bridge; this is not yet a durable Android foreground/background service, so leaving the app or Android reclaiming its process can interrupt the operation. It does **not** authenticate Microsoft accounts, configure a compatible Android Java runtime, construct the complete launch classpath/arguments, or launch Minecraft. The native resolver currently selects only x86/x86_64 Linux classifiers; it deliberately skips these binaries on ARM64 rather than accidentally installing incompatible x86 code. A compatible ARM64 runtime/native strategy is still required. Native extraction is bounded and path-checked, but the complete runtime/native-loading path still needs device validation. Library rule handling currently targets Linux and uses the running device's reported architecture; Windows/macOS rules and native classifiers are not implemented yet.

Remaining milestones:

1. Device and CPU architecture detection, storage planning, and device-aware memory limits.
2. Add a durable Android foreground installation service with lifecycle-safe progress/cancel controls, and validate complete library rule handling on supported Android devices.
3. Integrate a Java runtime compatible with supported Android devices and configure native library loading.
4. Build correct classpaths, JVM/game arguments, and process lifecycle handling for selected versions.
5. Implement secure Microsoft sign-in for licensed accounts.
6. Support local/offline profile UI without bypassing Minecraft authentication or licensing.
7. Add instance management, mod-loader integration, logs, crash reporting, and real-device tests across Android versions.

## Repository layout

- `index.html`, `mobile.css`, `app.js` — browser preview source
- `app/src/main/assets/` — UI files bundled into Android
- `app/src/main/java/com/hyperlauncher/mobile/` — native Android host, manifest reader, version-details, asset-index, and library resolvers, install planner, cancellable installation service, verified downloader, and JavaScript bridge
- `app/src/test/` — metadata, asset-index, resolver, library-rule, downloader safety, and installation cancellation tests
- `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts` — Android Gradle configuration
- `.github/workflows/android-build.yml` — automated debug APK build

The existing `main` branch is preserved.
