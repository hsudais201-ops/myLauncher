# myLauncher

myLauncher is an Android launcher project for Minecraft: Java Edition. The project keeps its new, independently tested Java core in `launcher/` and builds the real Android launcher on top of the pinned open-source Amethyst-Android engine.

## Two build targets

**Actual launcher APK:** the `Build myLauncher APK` workflow fetches the pinned Amethyst-Android source and submodules, applies the project's branding, low-memory defaults, and local-profile customizations, then builds the existing Java-game runtime/renderer launcher. Run it from GitHub Actions or open a pull request that changes `.github/workflows/build.yml` or `.github/scripts/**`. Download the `myLauncher-debug-apk` artifact (APK plus SHA-256 sidecar) from the completed run. The actual APK has not been device-tested by this core regression work; test it on an Android device before relying on gameplay.

**Core development shell:** the small native Android shell in `launcher/android-app/` is a separate test UI for version selection, file preparation and runtime discovery. It is intentionally not the playable Amethyst app. Its artifact is named `myLauncher-core-shell-debug-apk` so it cannot be confused with the full launcher.

## Isolated Java core

The product-owned code in `launcher/src/main/java/` contains validated version metadata (including Minecraft 26.1+ `default-user-jvm` recommendations and OS `versionRange` rules), safe verified downloads, installation planning, asset materialization, native archive extraction, verified classpath resolution, launch-argument generation, runtime checks and process supervision. Disabled launcher features default to false when no value is supplied. `default-user-jvm` entries are exposed as settings suggestions and are intentionally not forced into launches, because desktop defaults such as a 4 GiB heap are unsafe for some Android devices. The core has dependency-free regression tests, but it is not yet wired into Amethyst's existing launch screen; Amethyst remains the actual game-launch engine.

Run the core checks with a JDK:

    ./launcher/test.sh

Run the customizer regression tests with Python 3:

    python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v

Build the separate shell APK with JDK 17+, Gradle 8.9 and Android SDK platform/build-tools 35:

    cd launcher/android-app
    gradle :app:assembleDebug

The existing Android 9+ customization and upstream source pin are preserved on `backup/pre-clear-2026-10-16`. Keep the upstream license/notices and don't treat either a successful unit test or APK compilation as proof of successful Minecraft gameplay. Actual gameplay, Microsoft sign-in, rendering, runtime compatibility, thermal/memory pressure, and FPS still require device testing.
