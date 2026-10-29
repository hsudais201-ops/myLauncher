# Hyper Launcher — Android + Mobile UI

A mobile-first launcher prototype for Minecraft Java Edition. The repository now contains both the browser preview and an initial native Android app shell.

## Current working pieces

- Mobile-first Hyper Launcher home screen and touch-friendly navigation.
- Version selector and memory target controls.
- Browser-local preference persistence where storage is available.
- Android Gradle project using Kotlin and a WebView that loads the UI bundled in the APK.
- GitHub Actions workflow to build a debug APK and publish it as a workflow artifact.

## Build the Android preview

Pushes to `main`, pull requests, and manual workflow runs trigger the Android build workflow. When it succeeds, download the **hyper-launcher-debug-apk** artifact from the workflow run on GitHub Actions.

The Android project uses JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, and compile SDK 35.

## Important status — not a finished Minecraft launcher

The native app shell is only a first milestone. It **does not download, install, authenticate, or launch Minecraft yet**. A successful Android APK build would verify the shell compiles, not prove Minecraft gameplay works.

Remaining engineering milestones:

1. Device and CPU architecture detection, storage planning, and memory limits.
2. Fetch and validate official version manifests, libraries, assets, and client files.
3. Integrate a Java runtime that works on supported Android devices and configure native library loading.
4. Construct the correct game classpath, JVM arguments, game arguments, and process lifecycle.
5. Implement secure Microsoft account sign-in for licensed accounts.
6. Support local/offline profile UI without bypassing Minecraft authentication or licensing.
7. Add instance management, mod-loader integration, logs, crash reporting, and device testing across Android versions.

## Repository layout

- `index.html`, `mobile.css`, `app.js` — browser preview source
- `app/src/main/assets/` — UI files bundled into Android
- `app/src/main/java/com/hyperlauncher/mobile/MainActivity.kt` — native WebView host
- `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts` — Android Gradle configuration
- `.github/workflows/android-build.yml` — automated debug APK build

The existing `main` branch is preserved.
