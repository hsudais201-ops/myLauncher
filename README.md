# myLauncher

A personal Minecraft: Java Edition launcher build for Android, customized for low-memory devices.

**Runtime engine:** [Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android). This repository stores the myLauncher build customization and automation. It does not reimplement a Java virtual machine, OpenGL/LWJGL, account sign-in, game downloads, and mod loaders from scratch; those core runtime features come from the upstream open-source engine.

## Device target

- Android 9 (API 28) and newer. Upstream Amethyst declares a lower minimum Android SDK, so this build does not raise it.
- Intended for testing on devices with 2 GB RAM or less; performance is not guaranteed and varies with CPU, GPU, renderer, version, and free memory.
- Built-in low-memory defaults: the launcher starts with a 656 MB memory allocation when Android reports at most 2,048 MB total RAM (smaller devices keep Amethyst’s lower tiers). For devices in that RAM tier, the default resolution scaler targets a 480-pixel short screen edge when the edge is larger than 480 pixels; scaling is rounded to the nearest supported 5% step. Saved preferences still take priority, so users can change the values afterward.
- Suggested Minecraft settings: use 1.8.9 or 1.12.2, render distance 4–6 chunks, Fast graphics, and no shaders. Set these in Minecraft; this project does not force in-game settings.

## Build the APK

1. Open the Actions tab in this GitHub repository.
2. Run **Build myLauncher APK** using **Run workflow**, or push a change to `main`.
3. Open the completed workflow run and download its **myLauncher-debug-apk** artifact.
4. Transfer the APK to Android, install it, and allow installation from your download source if Android asks.

The workflow checks out the Amethyst source and submodules at pinned commit `330c6eae3164df64bdc4828e946a9e62cc5169e4`, applies the branding/package and low-memory customizations, then builds the debug APK. It verifies the upstream commit and fails if the branch moves unexpectedly. Update the pin deliberately when adopting a newer upstream version.

## Files

- `.github/workflows/build.yml` — repeatable Android build workflow.
- `.github/scripts/customize_amethyst.py` — deterministic patch for the app label, package/authority IDs, and low-memory defaults.
- `.github/scripts/test_customizer.py` — local regression tests for the patch logic.

## Local build

From this repository's root on Linux, clone the upstream source and then run the customization script:

    git clone --recursive --branch v3_openjdk https://github.com/AngelAuraMC/Amethyst-Android.git upstream
    git -C upstream fetch --depth 1 origin 330c6eae3164df64bdc4828e946a9e62cc5169e4
    git -C upstream checkout --detach 330c6eae3164df64bdc4828e946a9e62cc5169e4
    git -C upstream submodule update --init --recursive
    python3 .github/scripts/customize_amethyst.py upstream
    cd upstream
    ./gradlew :jre_lwjgl3glfw:build :app_pojavlauncher:assembleDebug

## Verification and limitations

The local tests validate the patching logic. GitHub Actions additionally compiles the app when a workflow run succeeds. A successful build is not proof of a real-device gameplay test: Microsoft sign-in, asset downloads, rendering, heat, memory pressure, and FPS still need checking on the target device.

## Upstream and licensing

This project builds and customizes [AngelAuraMC/Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android), which is based on PojavLauncher. Retain and follow the upstream license and notices when building or redistributing the APK. This repository provides the customization script so changes can be reviewed and reproduced.