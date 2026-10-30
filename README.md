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
- Install planner that includes the asset-index JSON, client JAR, and indexed asset objects with their expected sizes and SHA-1 hashes.
- Cancellable installation service that downloads the planned asset index, client JAR, and assets into app-private storage, reporting byte/file progress. It must be called from a worker thread and is not yet connected to the UI.
- Unit tests for version metadata, asset-index parsing, trusted download URLs, downloader path safety, and cancellation before the first download.
- GitHub Actions workflow to build a debug APK and upload it as an artifact.

## Build the Android preview

Pushes to `main` that change the Android project, pull requests, and manual workflow runs trigger the Android build workflow. When it succeeds, download the **hyper-launcher-debug-apk** artifact from that workflow run on GitHub Actions.

The Android project uses JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, and compile SDK 35. The build has been configured in the repository; a successful CI result has not yet been verified in this conversation.

## Important status — not a finished Minecraft launcher

The Android app is still an early prototype. The installation service can download and verify the asset-index JSON, client JAR, and indexed asset objects when called by code, but it is not yet connected to an install button or background UI workflow. It does **not** resolve/download version libraries or native classifiers, authenticate Microsoft accounts, configure a Java runtime, or launch Minecraft. Syncing official version IDs remains metadata-only.

Remaining milestones:

1. Device and CPU architecture detection, storage planning, and device-aware memory limits.
2. Connect the cancellable installation service to a background manager and UI with lifecycle-safe progress/cancel controls; then resolve/download version libraries and native classifiers.
3. Integrate a Java runtime compatible with supported Android devices and configure native library loading.
4. Build correct classpaths, JVM/game arguments, and process lifecycle handling for selected versions.
5. Implement secure Microsoft sign-in for licensed accounts.
6. Support local/offline profile UI without bypassing Minecraft authentication or licensing.
7. Add instance management, mod-loader integration, logs, crash reporting, and real-device tests across Android versions.

## Repository layout

- `index.html`, `mobile.css`, `app.js` — browser preview source
- `app/src/main/assets/` — UI files bundled into Android
- `app/src/main/java/com/hyperlauncher/mobile/` — native Android host, manifest reader, version-details and asset-index resolvers, install planner, cancellable installation service, verified downloader, and JavaScript bridge
- `app/src/test/` — metadata, asset-index, resolver, downloader safety, and installation cancellation tests
- `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts` — Android Gradle configuration
- `.github/workflows/android-build.yml` — automated debug APK build

The existing `main` branch is preserved.
