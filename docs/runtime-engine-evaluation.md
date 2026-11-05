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

Therefore Flame must not pretend that adding a single Gradle dependency enables game launching. The next integration work must preserve Amethyst's native runtime packaging and lifecycle, then connect Flame's UI to the real profile, download, authentication, and launch pipeline. Flame's standalone app now builds with AGP 9.3.1, Gradle 9.6.1, JDK 21, and SDK 37, but the native engine modules and NDK 27.3.13750724 are not yet configured in the Flame build.

## License and redistribution gate

Amethyst's top-level license is LGPL-3.0. The repository also credits components under different licenses, including MIT, zlib, BSD, GPL, and a component identified as "All Rights Reserved" (ANGLE). The integration must retain upstream copyright notices, ship the required license texts and corresponding source, and exclude or obtain separate redistribution permission for any component whose license does not allow the intended distribution. Do not copy a prebuilt runtime or native binary into Flame just because it is downloadable from upstream CI.

This note is an engineering assessment, not legal advice.

## Device preflight now implemented

Flame now evaluates the real Android API level and Android's reported CPU ABI list at runtime. It recognizes the four ABI targets documented by Amethyst, selects the first compatible ABI in Android's reported order, and records system-reported total memory when available.

This is a compatibility preflight only. It does **not** verify that a compatible JRE or renderer is installed, download Minecraft files, authenticate an account, or start the game. The Play screen continues to label the engine as not connected.

## Toolchain status

The standalone Flame build passed with AGP 9.3.1, Gradle 9.6.1, JDK 21, and Android API 37. The build has been migrated to AGP's built-in Kotlin support, removing the temporary `android.builtInKotlin=false` and `android.newDsl=false` opt-outs. The Compose compiler plugin remains applied at Kotlin 2.4.21. This is only the standalone app toolchain; it does not mean Amethyst's native modules build yet.

## Required next gates

1. Fetch the pinned Amethyst source shallowly and inspect all recursive submodules, native libraries, licenses, and generated runtime artifacts.
2. Choose and implement the same-APK host integration: bring Amethyst's subprojects into the Flame Gradle build, convert its launcher application module into a consumable library module, merge its app manifest and resources, and remove the upstream launcher intent filter from the merged manifest so Flame remains the single app entry point.
3. Add NDK 27.3.13750724 and the upstream native build/toolchain steps to CI, then build the complete APK with all native ABIs required by the supported device matrix.
4. Connect Flame's Play/profile UI to Amethyst's real storage, version downloader, authentication, and launch lifecycle without launching a separately installed app.
5. Validate on an actual ARM64 Android device: fresh install, runtime installation, official-account flow, local/offline profile where supported, vanilla launch, clean exit, and crash-log capture.
6. Audit LGPL notices and every native/runtime component's redistribution terms before shipping any bundled binaries or runtime assets.

A CI build alone is not evidence that Minecraft launches. The runtime milestone remains incomplete until the device-level launch checks pass.
