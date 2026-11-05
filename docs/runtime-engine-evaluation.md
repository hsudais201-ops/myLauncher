# Minecraft Java runtime evaluation

**Research checked:** 5 November 2026  
**Status:** Backend selected for integration evaluation; no game runtime has been integrated into Flame yet.

## Decision

Use [AngelAuraMC/Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android) as the primary runtime-engine candidate, rather than attempting to write a JVM, LWJGL, OpenGL bridge, audio stack, and native launch pipeline from scratch.

The upstream project identifies itself as the successor to PojavLauncher and documents support for Minecraft Java versions, Forge/Fabric, and prebuilt JREs. Its documented Android JRE targets include ARM64, ARM32, x86_64, and x86. The repository is active; the latest commit inspected for this evaluation was [28d6e12](https://github.com/AngelAuraMC/Amethyst-Android/commit/28d6e12d68121b7dd75f2e841058a6b7cdfe4a29), dated 26 October 2026.

Zalith Launcher 2 is a useful product and UX reference, and its README says it uses PojavLauncher as its core. However, Zalith Launcher 2 is GPL-3.0, while Amethyst is LGPL-3.0. Amethyst is the better licensing starting point for keeping Flame's independently authored application code separately licensed, subject to LGPL compliance and a component-by-component license audit.

## Integration constraints discovered

Amethyst is a complete Android application project, not a small Maven/AAR dependency. Its current source tree includes the launcher app, a custom LWJGL/GLFW runtime module, Forge installer components, JNI/native code, and Git submodules. Its inspected build configuration uses:

- Gradle 9.6.1.
- Android Gradle Plugin 9.3.1.
- Android SDK 37.
- Android NDK 27.3.13750724.
- Native dependencies and submodules including SDL and renderer-related components.

Therefore Flame must not pretend that adding a single Gradle dependency enables game launching. The next integration work must preserve Amethyst's native runtime packaging and lifecycle, then connect Flame's UI to the real profile, download, authentication, and launch pipeline. The existing Flame build (AGP 8.13.2 / Gradle 8.13 / SDK 36) is not yet configured for that integration.

## License and redistribution gate

Amethyst's top-level license is LGPL-3.0. The repository also credits components under different licenses, including MIT, zlib, BSD, GPL, and a component identified as "All Rights Reserved" (ANGLE). The integration must retain upstream copyright notices, ship the required license texts and corresponding source, and exclude or obtain separate redistribution permission for any component whose license does not allow the intended distribution. Do not copy a prebuilt runtime or native binary into Flame just because it is downloadable from upstream CI.

This note is an engineering assessment, not legal advice.

## Device preflight now implemented

Flame now evaluates the real Android API level and Android's reported CPU ABI list at runtime. It recognizes the four ABI targets documented by Amethyst, selects the first compatible ABI in Android's reported order, and records system-reported total memory when available.

This is a compatibility preflight only. It does **not** verify that a compatible JRE or renderer is installed, download Minecraft files, authenticate an account, or start the game. The Play screen continues to label the engine as not connected.

## Required next gates

1. Bring the pinned Amethyst source and its required Git submodules into a dedicated engine area, retaining upstream provenance and license files.
2. Integrate the engine into one Flame APK, rather than requiring a separate launcher installation.
3. Update CI for the required Gradle, Android SDK, NDK, and recursive submodules, then build the complete native app.
4. Connect the Flame Play/profile UI to the engine's real storage, version downloader, account, and launch lifecycle.
5. Validate on an actual ARM64 Android device: fresh install, runtime installation, official-account flow, local/offline profile where supported, vanilla launch, clean exit, and crash-log capture.

A CI build alone is not evidence that Minecraft launches. The runtime milestone remains incomplete until the device-level launch checks pass.
