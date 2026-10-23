# Voidcraft rendering engine integration plan

## Current state

The Settings → Render Backend screen is a diagnostic screen, not the Minecraft rendering engine. It draws a rotating, depth-tested 3D cube through Android OpenGL ES 2.0 and reports the active GPU/driver, GL version, maximum texture size, and surface dimensions. Keep the launcher Play action unavailable until a real launch pipeline is integrated and tested.

## Why the diagnostic surface is not enough

Minecraft: Java Edition and its LWJGL window/input stack expect desktop OpenGL APIs. Android devices typically expose OpenGL ES. A compatible renderer layer must translate or implement the desktop OpenGL calls expected by the chosen Minecraft/LWJGL version. See the PojavLauncher renderer guide:
https://github.com/TeamPojavLauncher/teampojavlauncher.github.io/blob/main/markdown/wiki/faq/android/RENDERERS.md

## Recommended path

Evaluate a maintained Android Java launcher backend before implementing a graphics compatibility layer from scratch. The AngelAuraMC Amethyst-Android project is a relevant reference because it includes an Android launch stack and rendering/native components:
https://github.com/AngelAuraMC/Amethyst-Android

Do not wholesale-copy upstream code into this prototype without first reviewing the exact current source, submodules, build instructions, ABI requirements, and every applicable license. The Amethyst repository describes itself as LGPLv3 and lists components with multiple licenses. Preserve notices and meet source-distribution obligations for any reused or modified components; obtain legal advice for distribution questions.

## Renderer choices to evaluate

- **GL4ES:** a practical compatibility option for many vanilla versions; the cited PojavLauncher renderer guide describes OpenGL 2.1-level support and shader conversion.
- **LTW:** a more modern compatibility option aimed at a larger OpenGL feature set and newer versions/mods, with device and mod compatibility caveats.
- **Vulkan-based paths (such as Zink):** potentially useful on supported GPUs, but require Vulkan support and a compatible native build; do not enable by default without device testing.

References:
- https://github.com/TeamPojavLauncher/teampojavlauncher.github.io/blob/main/markdown/wiki/faq/android/RENDERERS.md
- https://pojav.ru/patchnotes/changelogs/ANDROID.html

## Integration phases

1. **Choose and pin an upstream backend.** Record its commit/release, supported ABIs, Android SDK/NDK requirements, submodules, native dependencies, and license notices.
2. **Bring in the native/runtime layer.** Package or install only trusted, version-pinned native libraries for each supported ABI (arm64-v8a, armeabi-v7a, x86_64, and x86 only where upstream supports them). Validate hashes where published and fail safely on unsupported ABI combinations.
3. **Connect Java/LWJGL startup.** Select a Java runtime compatible with the chosen game version and loader; provide LWJGL/GLFW shims and native loading in the order required by upstream. A Java executable alone is not enough.
4. **Install a game version.** Resolve Mojang's version JSON, inherited version metadata, client JAR, libraries, asset index/assets, logging configuration, and native dependencies. Verify downloaded artifacts against trusted metadata before launch.
5. **Own the game surface and input lifecycle.** Connect the actual engine surface to pause/resume, resizing, orientation changes, touch controls, keyboard/mouse, clipboard, and clean process shutdown. The current GLES diagnostic screen should remain separately available for troubleshooting.
6. **Keep accounts secure.** Implement Microsoft's documented authentication flow without collecting or storing account passwords in Voidcraft. Keep tokens out of logs and use a documented revocation/sign-out path.
7. **Gate launch on preflight checks.** Show selected version/runtime, ABI support, free storage/memory estimates, required libraries, and renderer availability before enabling Play. Preserve crash logs for user-exportable troubleshooting, with secrets redacted.
8. **Test before calling it playable.** Test at least one older Java 8-targeting profile and one modern profile on real ARM64 hardware; add ARM32/x86/x86_64 only where supported. Verify vanilla launch, renderer initialization, world entry, resize/resume, input, graceful exit, and failure recovery before testing mod loaders.

## Acceptance criteria

- A user can install a supported Minecraft version from official metadata and all required libraries/assets.
- Launch creates a game surface and reaches the title screen on a documented device/runtime/renderer combination.
- The renderer reports a useful startup error instead of falsely claiming success when required native libraries or GL features are missing.
- Suspending/resuming the app and rotating/resizing the display do not leave a permanently black or detached surface.
- The selected Java runtime, game version, renderer, and native ABI combination is validated before launch.
- No proprietary game assets or authentication secrets are bundled into public repository artifacts.

Until these checks pass, describe Voidcraft as a launcher prototype with a GPU diagnostic, not a playable Minecraft Java launcher.

## Upstream build feasibility note (reviewed 2026-10-22)

The current Amethyst-Android README documents a recursive clone (`git clone --recursive https://github.com/AngelAuraMC/Amethyst-Android.git`) and a debug build target (`./gradlew :app_pojavlauncher:assembleDebug`). Its documented architecture includes custom OpenJDK mobile builds, a custom LWJGL/GLFW bridge, and native audio/graphics components. The upstream README also lists support for modern snapshots, but this is not a guarantee that every Minecraft version, Android device, mod, or ABI will work in Voidcraft. See the upstream build instructions and license/dependency inventory before choosing a pinned revision:
https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/README.md

**Next engineering decision:** build the selected upstream in an isolated CI job first and capture its exact commit SHA, artifact provenance, and license inventory. Only after that succeeds should we choose between (a) integrating a clearly separated upstream launcher module or (b) adapting individual components with their license obligations. Do not download and bundle a random prebuilt APK as the engine: that would not provide a maintainable integration with Voidcraft's settings, version manager, Feature Store, or lifecycle.

## Reproducible upstream probe

The repository now includes a manual GitHub Actions workflow at `.github/workflows/upstream-engine-probe.yml`. Open **Actions → Upstream engine compatibility probe → Run workflow**, keep `v3_openjdk` as the default reference (or enter a reviewed tag/commit), and start the isolated build. The workflow checks out Amethyst with recursive submodules, records the resolved commit and license-file inventory, runs `:app_pojavlauncher:assembleDebug`, and uploads any APK plus the inventory as an artifact even if the build fails. This is an investigation artifact only; it does not merge upstream code into Voidcraft or make the Voidcraft app launch Minecraft.

Before adopting the upstream APK or source, review the recorded commit, the full dependency/license inventory (including bundled native libraries and submodules), and whether the build relies on external or proprietary assets. Do not publish the probe APK as a Voidcraft release.
