# Hyper Launcher — Android + Mobile UI

A mobile-first launcher prototype for Minecraft Java Edition. The repository contains the browser preview and an initial native Android app shell.

## Current working pieces

- Mobile-first Hyper Launcher home screen and touch-friendly navigation.
- Native instance manager with persistent profiles, create/select/rename/delete controls, a preserved Default profile that keeps the legacy game folder, and separate app-private game/content directories per new instance.
- Version selector and memory target controls, with Android-side CPU ABI, RAM, and free app-storage reporting plus a conservative device-specific suggested memory range. The memory target is stored as a preference and is not yet applied to a running game process.
- Browser-local preference persistence where storage is available.
- Native Android Kotlin app shell that loads the UI bundled inside the APK, restricts top-level WebView navigation to bundled assets, and disables file-URL cross-origin access so the JavaScript bridge is not exposed to remote pages.
- Android-side reader for Mojang's public version manifest, with a UI control to sync official release and snapshot version IDs.
- Manifest size limits, network timeouts, HTTPS host allowlists, SHA-1 validation, and unit tests for parsing/filtering version metadata.
- Per-version metadata resolver that verifies the official metadata SHA-1 and extracts client JAR and asset-index download descriptors.
- App-private file downloader foundation with trusted-host redirect checks, path-traversal protection, exact-size checks, SHA-1 verification, temporary files, and atomic finalization.
- Asset-index parser that validates object hashes and sizes and builds content-addressed asset paths.
- Launch-configuration resolver that combines official version launch metadata, platform/feature-aware JVM and game arguments, a verified installed classpath, native-directory path, and standard instance/assets placeholders; it leaves authentication/session placeholders to a real account flow and is not yet executed by a Java runtime.
- Safe installed-artifact classpath resolver that canonicalizes every client/library path beneath the selected instance root, rejects traversal and symlink escapes, checks that artifacts exist and are readable, and joins paths using the platform separator. This utility is tested but not yet called by a game process.
- Structured Mojang game/JVM launch-argument resolver foundation: reads legacy and modern argument metadata, evaluates OS/architecture/version and feature rules, substitutes supplied placeholders, and preserves argument token boundaries without invoking a shell. This is not yet connected to a Java runtime or game process.
- Android install planner that includes the asset-index JSON, client JAR, Android-rule-compatible Maven artifacts, and indexed asset objects with expected sizes and SHA-1 hashes. Desktop Linux native classifier archives are deliberately excluded on Android until an Android-native runtime/graphics integration is present.
- Library resolver applies the current Linux-family Mojang rules and validates artifact paths and trusted HTTPS hosts. The native-classifier resolver refuses desktop Linux native archives on Android, including x86_64 Android, because matching CPU architecture does not make glibc-linked libraries Android-compatible.
- Bounded, path-safe native ZIP extraction utility for a future Android-compatible native bundle; it is not currently used to install desktop Linux natives on Android.
- Native Modrinth and CurseForge catalog browser with search for mods, modpacks, resource packs, shaders, data packs, server plugins, and CurseForge worlds, plus Minecraft-version filters, loader filters where relevant, sorting, and paginated search results. Modrinth search works without credentials; CurseForge search uses an API key entered by the user and held in memory only for the current app session. Project links open on the official catalog sites. Selected project files can be downloaded into the matching app-private `mods`, `resourcepacks`, `shaderpacks`, `datapacks`, `plugins`, `worlds`, or `modpacks` folder after source metadata, HTTPS host, file size, and SHA-1 checks. Modpack, server-plugin, and world archives are saved only; automatic modpack/world import is not implemented and server plugins are not part of the client mod directory.
- Android install controls connected to the native bridge, with progress reporting and a cancel action. The bridge resolves the selected official version, builds the plan, and runs the cancellable installation service on a worker thread.
- Cancellable installation service that checks free app-storage space before downloading, then downloads the planned asset index, client JAR, standard libraries, and indexed assets, and reports download byte/file progress. Android native runtime libraries are not yet installed by this service. The preflight reserves 32 MiB for filesystem and temporary-file overhead.
- Unit tests for version metadata, asset-index parsing, trusted download URLs, downloader path safety, and cancellation before the first download.
- GitHub Actions workflow to run JVM unit tests, build a debug APK, and upload it as an artifact.

## Build the Android preview

Pushes to `main` that change the Android project, pull requests, and manual workflow runs trigger the Android build workflow. When it succeeds, download the **hyper-launcher-debug-apk** artifact from that workflow run on GitHub Actions. The debug APK has been built and uploaded successfully by CI. The expanded CI workflow runs the JVM unit-test suite and APK packaging; check the latest workflow run for the current commit before treating a build as verified.

The Android project uses JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, and compile SDK 35. The Android debug build has been verified successful in GitHub Actions. The CI workflow runs `testDebugUnitTest` before `assembleDebug`; a complete test-and-build run has passed on earlier commits.

## Important status — not a finished Minecraft launcher

The Android app is still an early prototype. Instance management supports creating, selecting, renaming, and deleting profiles; new profiles use separate app-private storage roots, while the Default profile preserves the existing legacy game directory. Minecraft version installation and mod downloads use the selected profile's storage root. The installer downloads and verifies the official asset-index JSON, client JAR, compatible Maven artifacts, and indexed assets. It intentionally does **not** install desktop Linux native classifiers on Android (even on x86_64), because desktop Linux binaries are not interchangeable with Android native libraries. A bounded native ZIP extraction utility exists, but a compatible Android runtime/native bundle is not connected. Download progress and cancellation are supported; downloads are not yet a durable Android foreground service and may be interrupted if Android reclaims the app process. The launch-configuration layer combines Mojang's game/JVM arguments with the validated client/library classpath and standard paths, but it is not executed by a Java runtime. Microsoft authentication, a compatible Android Java runtime, graphics/input integration, and actual game-process lifecycle are still missing. The Modrinth/CurseForge browser can discover projects, list compatible files, and download selected files after metadata and checksum verification. These files are saved in matching content folders, but mods are not active in-game because Minecraft cannot yet launch.

### Android runtime integration research

The current candidate for a real Android Minecraft runtime is [Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android), the active successor to [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher). Amethyst's upstream project documents Android Java runtimes for ARM32, ARM64, x86, and x86_64, plus its custom LWJGL and graphics/native components. It is licensed under LGPL-3.0 and has multiple native and third-party dependencies, so integration must preserve license notices, source/relinking obligations, and upstream attribution. Hyper Launcher has **not** bundled or integrated Amethyst yet. A normal desktop JRE or Linux native archive is not a substitute.

## Repository layout

- `index.html`, `mobile.css`, `app.js` — browser preview source
- `app/src/main/assets/` — UI files bundled into Android
- `app/src/main/java/com/hyperlauncher/mobile/` — native Android host, manifest reader, Modrinth/CurseForge search adapter, version-details, asset-index, library, classpath and launch-configuration resolvers, install planner, cancellable installation service, verified downloader, and JavaScript bridge
- `app/src/test/` — metadata, asset-index, launch-argument, classpath and launch-configuration resolver, library-rule, downloader safety, and installation cancellation tests
- `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts` — Android Gradle configuration
- `.github/workflows/android-build.yml` — automated debug APK build

The existing `main` branch is preserved.


#### Upstream runtime integration findings (31 October 2026)

A deeper inspection of Amethyst's `v3_openjdk` source confirms that its runtime support is not a standalone Java-runtime library. Its Android app module contains many bundled native AARs (including SDL, graphics backends, OpenAL, and LWJGL native bundles), a JNI/NDK build, Java runtime management code, and Git submodules such as MioLibPatcher and androidnsbypass. Its runtime manager stores each runtime as a directory with a `release` descriptor and a `bin/java` executable; it checks the Java major version and architecture metadata before selecting a runtime. External Android JRE archives are downloaded per Java major version and device architecture.

This means copying `NewJREUtil.java` or adding a generic Maven dependency would not integrate a working runtime. The viable route is a deliberate upstream module/fork integration that carries the matching native libraries, graphics bridge, JNI build, submodules, and LGPL-3.0 compliance. Until that is done, Hyper Launcher must continue to report runtime readiness as unavailable rather than infer it from CPU architecture or downloaded desktop libraries.
