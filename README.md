# VoidCraft Launcher

**Owner / developer: MF**

A personal Minecraft: Java Edition launcher build for Android, customized for low-memory devices.

**Runtime engine:** [Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android). This repository stores the VoidCraft Launcher build customization and automation. It does not reimplement a Java virtual machine, OpenGL/LWJGL, account sign-in, game downloads, and mod loaders from scratch; those core runtime features come from the upstream open-source engine.

See [FEATURE_ROADMAP.md](FEATURE_ROADMAP.md) for the complete 23-section feature scope, current source audit, and staged delivery plan. Items in that list are tracked requirements, not claims that every feature is implemented or runtime-verified.

## Device target

- Android 9 (API 28) and newer. Upstream Amethyst declares a lower minimum Android SDK, so this build does not raise it.
- Intended for testing on devices with 2 GB RAM or less; performance is not guaranteed and varies with CPU, GPU, renderer, version, and free memory.
- Built-in low-memory defaults: for devices reporting at most 2,048 MB total RAM, the allocator uses 296 MB below 1,024 MB, 448 MB below 1,536 MB, and a reduced 512 MB heap from 1,536–2,048 MB. The default resolution scaler targets a 480-pixel short screen edge for that RAM tier when the physical edge is larger than 480 pixels; scaling is rounded upward to the next supported 5% step. Saved preferences still take priority, so users can change the values afterward.
- Suggested Minecraft settings: use 1.8.9 or 1.12.2, render distance 4–6 chunks, Fast graphics, and no shaders. Set these in Minecraft; this project does not force in-game settings.

## Account support and interface

- The launcher retains Amethyst's Microsoft account sign-in and local/offline profile flow. The custom patch removes the online-account gate in the account selector and the local-login screen, so a local profile can be created without first adding a Microsoft account. The local username field is configured as a username field.
- An offline profile is only a local player name; it is **not** a Microsoft-authenticated Minecraft account. Servers requiring authenticated/online-mode accounts may reject it. Do not use offline mode to impersonate another player.
- An offline profile does not supply game files or bypass Minecraft licensing. When internet is available, VoidCraft Launcher can download public Minecraft version metadata, libraries, client files, and assets for a local profile without Microsoft sign-in. Without internet, the selected version must already be fully installed.

### Troubleshooting: missing Minecraft version metadata

If the launcher reports `FileNotFoundException` for a path like `versions/<version>/<version>.json` or says the version metadata is missing, the selected installation is incomplete or its metadata could not be read.

1. Connect to a working internet connection, return to VoidCraft Launcher, select the same Minecraft version, and press **Play** again. The launcher should be able to download the missing public version files without requiring Microsoft sign-in just because you use a local profile.
2. Keep the launcher open until downloads finish. Do not create an empty `.json` file manually; it is not valid version metadata.
3. If the error continues, copy the exact version ID and the relevant error from the launcher log. Do not share Microsoft access tokens or other account secrets.
4. When offline, use a version whose metadata, client, libraries, and assets are already installed. Creating a local profile alone does not install Minecraft files.
- Local profiles can browse their game directory. Some upstream utilities, including the JAR installer, retain the upstream online-account requirement; this customization does not remove those separate restrictions.
- The launcher home screen gets a lightweight VoidCraft Launcher title/subtitle header plus a dedicated **Add account** shortcut in portrait and landscape. The shortcut opens the existing Microsoft/local account selector.
- The Android launcher icon is replaced by a lightweight vector mark matching the new dark/purple identity. No UI library or significant runtime dependency is added.

## Build the APK

1. Open the Actions tab in this GitHub repository.
2. Build validation runs automatically on pushes to `main` and pull requests when `.github/workflows/build.yml` or `.github/scripts/**` changes. You can also run **Build VoidCraft Launcher APK** manually using **Run workflow** in the Actions tab.
3. Open the completed workflow run and download its **voidcraft-launcher-debug-apk** artifact. The artifact includes the APK and a `.sha256` checksum sidecar.
4. Before installing, verify the transfer on Linux with `sha256sum -c <apk-filename>.sha256` from the directory containing the APK and sidecar. Then transfer the APK to Android, install it, and allow installation from your download source if Android asks.

The workflow fetches the exact Amethyst commit `330c6eae3164df64bdc4828e946a9e62cc5169e4` and its submodules, rewrites SSH-style public GitHub submodule URLs to HTTPS for hosted runners, validates submodule checkout status, validates the customization regression suite before installing the Android toolchain, applies the customizations, and builds the debug APK. The source pin is independent of branch movement. Update the pin deliberately when adopting a newer upstream version.

## Files

- `.gitignore` — excludes local upstream checkouts, Python caches, and build outputs.
- `.github/workflows/build.yml` — repeatable Android build workflow, triggered by relevant pushes to `main`, pull requests, or a manual dispatch.
- `.github/workflows/runner-diagnostic.yml` — minimal hosted-runner diagnostic workflow.
- `.github/scripts/customize_amethyst.py` — deterministic patch for app branding, package/authority IDs, low-memory defaults, offline account access, launcher header, and folder/account shortcuts.
- `.github/scripts/apply_logo.py` — applies and validates the final Android vector app icon used by CI.
- `assets/branding/mylauncher-logo.svg` — high-resolution SVG brand artwork for non-Android uses and reference.
- `.github/scripts/test_customizer.py` — regression tests for branding, offline-account username syntax/length/duplicate-profile and boolean-logic guards, account submission and labeling, auth-selector listener/navigation safety, local game-directory access, both launcher layouts, XML well-formedness, missing-anchor failures, repeat-run idempotency, Android toolchain alignment, and CI submodule/APK safeguards.

## Local build

From this repository's root on Linux, use Python 3, Git, JDK 21, Android SDK command-line tools, platform `android-37`, and NDK `27.3.13750724`. First run the regression suite:

    python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v

Then fetch the pinned upstream source and build:

    git init upstream
    git -C upstream remote add origin https://github.com/AngelAuraMC/Amethyst-Android.git
    git -C upstream fetch --depth 1 origin 330c6eae3164df64bdc4828e946a9e62cc5169e4
    git -C upstream checkout --detach FETCH_HEAD
    git config --global url."https://github.com/".insteadOf git@github.com:
    git config --global --add url."https://github.com/".insteadOf ssh://git@github.com/
    git -C upstream submodule sync --recursive
    git -C upstream submodule update --init --recursive
    yes | sdkmanager --licenses
    sdkmanager --install 'platforms;android-37.0' 'ndk;27.3.13750724' --channel=3
    python3 .github/scripts/customize_amethyst.py upstream
    python3 .github/scripts/apply_logo.py upstream
    cd upstream
    ./gradlew :jre_lwjgl3glfw:build :app_pojavlauncher:assembleDebug

## Verification and limitations

The latest completed baseline `main` build at the start of this audit was [run #39035093349](https://github.com/hsudais201-ops/myLauncher/actions/runs/39035093349) on commit `3c0b4359c92445ae7ddcc7eacb2858110adf899a`. It passed the repository regression suite, pinned Amethyst checkout/submodules, Gradle APK build, APK signature/package/minimum-SDK checks, and Android 9/API 28 emulator install-and-launch smoke test. The workflow uploaded the debug APK with a SHA-256 checksum sidecar, build information, and emulator diagnostics.

The **World saves** shortcut was also validated in [PR #4 workflow run #38996198756](https://github.com/hsudais201-ops/myLauncher/actions/runs/38996198756) before merge. This audit branch additionally hardens offline username validation and corrects the local-build instructions; its own CI result must be checked before merging.

The cloud emulator smoke test verifies installation and that `LauncherActivity` becomes active. It does not prove that each folder button works through a physical tap, Microsoft sign-in succeeds, a Minecraft version downloads fully, a world starts, or rendering/controllers/performance work on a physical phone or Chromebook. Interactive device and gameplay acceptance tests remain necessary.

The shipped folder shortcuts open each selected profile's `mods/`, `resourcepacks/`, and `saves/` directories and create them if missing. They provide manual file access, not full mod/resource-pack installation, activation, conflict detection, compatibility checking, automated world backup, restore, or cloud sync.

The source patcher is regression-tested and the current `main` APK is signature-verified by CI. The project still builds upstream Amethyst pinned at `330c6eae3164df64bdc4828e946a9e62cc5169e4`; VoidCraft Launcher customizes that runtime rather than replacing its Java VM or game engine. Review [FEATURE_ROADMAP.md](FEATURE_ROADMAP.md) to distinguish inherited engine features, VoidCraft Launcher-specific changes, and planned features.

### Missing-version metadata recovery

If the launcher shows `FileNotFoundException` for a path such as `versions/<version>/<version>.json` or reports missing, unreadable, or empty metadata, treat that Minecraft installation as incomplete.

1. Connect to a working internet connection, return to VoidCraft Launcher, select the same version, and press **Play** again. Connected local/offline profiles can download public version files without adding a Microsoft account.
2. Leave the launcher open until the downloads finish. Do not create an empty JSON file manually.
3. If the error repeats, record the selected version ID and relevant launcher log lines. Do not share Microsoft tokens or other account secrets.
4. Without internet, use a version whose metadata, client, libraries, and assets are already installed. Creating a local profile alone does not install Minecraft.

## Manual device smoke test

After a debug APK is available on the target Android 9 device:

1. Install the APK and open **Add account → Local account**.
2. Create a unique username with 3–16 letters, digits, or underscores and confirm it appears in the account spinner.
3. Select a lightweight Minecraft version (for example 1.8.9); while connected to the internet, let the launcher download it if needed, then start the game and test a single-player world.
4. Return to the launcher and verify that **Open game directory** works with only the local profile.
5. Verify the Microsoft account button still opens Microsoft sign-in. Tap **Mods folder**, **Resource packs**, and **World saves** and confirm they open the selected profile's `mods/`, `resourcepacks/`, and `saves/` directories. These actions have source/regression and launcher-start CI coverage, but their taps still require interactive acceptance testing. An actual device test is still required for memory pressure, rendering, and gameplay stability.

## Upstream and licensing

This project builds and customizes [AngelAuraMC/Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android), which is based on PojavLauncher. Retain and follow the upstream license and notices when building or redistributing the APK. This repository provides the customization script so changes can be reviewed and reproduced.