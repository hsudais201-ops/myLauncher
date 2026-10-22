# Voidcraft Android Launcher

Voidcraft is a touch-first Android launcher frontend for Minecraft: Java Edition. The `launcher/bootstrap` branch contains the Compose UI plus a build-time integration path to the pinned Amethyst Android launch/render engine.

## Current working pieces

- **Launcher frontend and Feature Store:** optional and opt-in; only catalog entries are shown, and built-in tools require an explicit Install action before Open is enabled.
- **Social Hub:** local contacts/notes plus an online staging beta for accounts, friend requests, and direct messages. This is not production-secure.
- **Theme Studio and Cosmetic Store:** persisted launcher accent/preset choices and local profile preview effects. Cosmetic previews are not Minecraft skins or multiplayer cosmetics.
- **Versions and preparation:** reads Mojang's official version manifest, can search older release/beta/alpha entries, verifies the selected version JSON against the manifest SHA-1, and can download the client JAR, metadata-listed library JARs, asset index, and assets with size/path/host/ SHA-1 checks. Game assets require a separate confirmation because they can consume multiple gigabytes and mobile data.
- **Active game folders in the integrated build:** version preparation targets the engine's version/library/assets folders; the file library and Advanced Mod Manager target its configured game folder and mods directory. Mods need the right game version and mod loader, and changes require a game restart.
- **Settings links:** profile/account, runtime/game settings, performance and touch-control pages can open the actual engine's account/runtime/game settings or control-layout editor in the combined build. Standalone preferences remain local preferences and should not be confused with engine settings.
- **Local utility tools:** local player-name preference, memory advisor, touch preference storage, file import/export, skin PNG validation/preview and GitHub release checker.
- **Render diagnostics:** the original standalone OpenGL ES 2.0 diagnostic cube reports GPU details and shader/draw errors. In the combined build, actual Minecraft rendering is supplied by the upstream Android engine, not by this cube.

## Builds

### Standalone Voidcraft UI prototype

- Application ID: `com.voidcraft.launcher`
- Minimum Android: API 26
- JDK: 17
- Compile SDK: 35

Open this repo in Android Studio or run the normal Android build workflow. It uploads an artifact named `voidcraft-debug-apk`. **That standalone artifact does not include the Minecraft game-launch engine.**

### Combined Voidcraft + Android game engine

Use [the integrated engine build workflow](https://github.com/hsudais201-ops/myLauncher/actions/workflows/voidcraft-engine-build.yml). It checks out Amethyst-Android at pinned revision [`330c6eae3164df64bdc4828e946a9e62cc5169e4`](https://github.com/AngelAuraMC/Amethyst-Android/tree/330c6eae3164df64bdc4828e946a9e62cc5169e4), checks out its submodules, preserves the upstream first-run storage/native bootstrap, overlays the Voidcraft Compose UI, then builds `:app_pojavlauncher:assembleDebug`. That workflow intends to upload `voidcraft-integrated-debug-apk` with a SHA-256 checksum, pinned-source provenance, and license-file inventory.

The combined workflow must finish with a successful build before its artifact can be used. A successful CI build is still not proof that the APK launches on every physical device.

## Important launch and release gates

The combined app's intended flow is: upstream first-run storage/runtime initialization → Voidcraft home → **PLAY** → upstream engine launcher. The backend launcher handles its own supported authentication, Java runtime registration, game-version/mod-loader setup, native rendering, and launch options. The user must complete its required setup and start the game; Voidcraft does not bypass Microsoft authentication.

Before calling Voidcraft a playable/public release, all of these must be verified:

- The latest combined debug APK builds and installs on supported physical Android devices.
- Upstream first-run bootstrap, official sign-in, Java runtime selection, vanilla version installation, and a real single-player world launch work on device.
- Touch controls, keyboard/gamepad input, audio, renderer selection, screen rotation/resume, storage, and crash logs work on the targeted devices/ABIs.
- Version/client/libraries/assets and mod/file tools use the same actual game directory.
- All upstream and transitive component license/redistribution obligations are reviewed and satisfied. See [the engine license and release checklist](docs/ENGINE_LICENSE_REVIEW.md). Some credits in the pinned upstream README identify components with unclear or restrictive redistribution terms; do not publish the combined APK until those are resolved.
- Release signing, versioning, source/notices availability, privacy/security review, and website distribution are prepared.

Until those checks pass, artifacts are **debug/test builds**, not a verified public release.

## Security notes

- Never commit OAuth secrets, signing keys, access tokens, or personal account data.
- Use unique test passwords for the Social Hub staging beta; it lacks email verification, password recovery, push notifications, and end-to-end encryption.
- Download mods only from sources you trust, and confirm that each mod matches the chosen game version and loader.
