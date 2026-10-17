# myLauncher

A personal Minecraft: Java Edition launcher build for Android, customized for low-memory devices.

**Runtime engine:** [Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android). This repository stores the myLauncher build customization and automation. It does not reimplement a Java virtual machine, OpenGL/LWJGL, account sign-in, game downloads, and mod loaders from scratch; those core runtime features come from the upstream open-source engine.

See [FEATURE_ROADMAP.md](FEATURE_ROADMAP.md) for the complete 23-section feature scope, current source audit, and staged delivery plan. Items in that list are tracked requirements, not claims that every feature is implemented or runtime-verified.

## Device target

- Android 9 (API 28) and newer. Upstream Amethyst declares a lower minimum Android SDK, so this build does not raise it.
- Intended for testing on devices with 2 GB RAM or less; performance is not guaranteed and varies with CPU, GPU, renderer, version, and free memory.
- Built-in low-memory defaults: for devices reporting at most 2,048 MB total RAM, the allocator uses 296 MB below 1,024 MB, 448 MB below 1,536 MB, and a reduced 512 MB heap from 1,536–2,048 MB. The default resolution scaler targets a 480-pixel short screen edge for that RAM tier when the physical edge is larger than 480 pixels; scaling is rounded upward to the next supported 5% step. Saved preferences still take priority, so users can change the values afterward.
- Suggested Minecraft settings: use 1.8.9 or 1.12.2, render distance 4–6 chunks, Fast graphics, and no shaders. Set these in Minecraft; this project does not force in-game settings.

## Account support and interface

- The launcher retains Amethyst's Microsoft account sign-in and local/offline profile flow. The custom patch removes the online-account gate in the account selector and the local-login screen, so a local profile can be created without first adding a Microsoft account. The local username field is configured as a username field.
- An offline profile is only a local player name; it is **not** a Microsoft-authenticated Minecraft account. Servers requiring authenticated/online-mode accounts may reject it. Do not use offline mode to impersonate another player.
- An offline profile does not supply game files or bypass Minecraft licensing. When internet is available, myLauncher can download public Minecraft version metadata, libraries, client files, and assets for a local profile without Microsoft sign-in. Without internet, the selected version must already be fully installed.

### Troubleshooting: missing Minecraft version metadata

If the launcher reports `FileNotFoundException` for a path like `versions/<version>/<version>.json` or says the version metadata is missing, the selected installation is incomplete or its metadata could not be read.

1. Connect to a working internet connection, return to myLauncher, select the same Minecraft version, and press **Play** again. The launcher should be able to download the missing public version files without requiring Microsoft sign-in just because you use a local profile.
2. Keep the launcher open until downloads finish. Do not create an empty `.json` file manually; it is not valid version metadata.
3. If the error continues, copy the exact version ID and the relevant error from the launcher log. Do not share Microsoft access tokens or other account secrets.
4. When offline, use a version whose metadata, client, libraries, and assets are already installed. Creating a local profile alone does not install Minecraft files.
- Local profiles can browse their game directory. Some upstream utilities, including the JAR installer, retain the upstream online-account requirement; this customization does not remove those separate restrictions.
- The launcher home screen gets a lightweight myLauncher title/subtitle header plus a dedicated **Add account** shortcut in portrait and landscape. The shortcut opens the existing Microsoft/local account selector.
- The Android launcher icon is replaced by a lightweight vector mark matching the new dark/purple identity. No UI library or significant runtime dependency is added.

## Build the APK

1. Open the Actions tab in this GitHub repository.
2. Build validation runs automatically on pushes to `main` and pull requests when `.github/workflows/build.yml` or `.github/scripts/**` changes. You can also run **Build myLauncher APK** manually using **Run workflow** in the Actions tab.
3. Open the completed workflow run and download its **myLauncher-debug-apk** artifact. The artifact includes the APK and a `.sha256` checksum sidecar.
4. Before installing, verify the transfer on Linux with `sha256sum -c <apk-filename>.sha256` from the directory containing the APK and sidecar. Then transfer the APK to Android, install it, and allow installation from your download source if Android asks.

The workflow fetches the exact Amethyst commit `330c6eae3164df64bdc4828e946a9e62cc5169e4` and its submodules, rewrites SSH-style public GitHub submodule URLs to HTTPS for hosted runners, validates submodule checkout status, validates the customization regression suite before installing the Android toolchain, applies the customizations, and builds the debug APK. The source pin is independent of branch movement. Update the pin deliberately when adopting a newer upstream version.

## Files

- `.gitignore` — excludes local upstream checkouts, Python caches, and build outputs.
- `.github/workflows/build.yml` — repeatable Android build workflow, triggered by relevant pushes to `main`, pull requests, or a manual dispatch.
- `.github/workflows/runner-diagnostic.yml` — minimal hosted-runner diagnostic workflow.
- `.github/scripts/customize_amethyst.py` — deterministic patch for app branding, package/authority IDs, low-memory defaults, offline account access, launcher header, account shortcut, and vector app icon.
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
    cd upstream
    ./gradlew :jre_lwjgl3glfw:build :app_pojavlauncher:assembleDebug

## Verification and limitations

The latest full feature-branch validation is [GitHub Actions run #38995241453](https://github.com/hsudais201-ops/myLauncher/actions/runs/38995241453) for [PR #3](https://github.com/hsudais201-ops/myLauncher/pull/3), which added the Resource packs shortcut. It passed the Python regression suite, Android SDK/NDK setup, pinned Amethyst source checkout and submodules, customization plus `git diff --check`, Gradle build, APK signature and package/minimum-SDK checks, and the Android 9/API 28 headless emulator install-and-launch smoke test. The APK artifact includes a SHA-256 checksum sidecar; build information and emulator diagnostics were uploaded. The feature has been merged to `main`; post-merge verification is running in [run #38995666191](https://github.com/hsudais201-ops/myLauncher/actions/runs/38995666191).

The current smoke test verifies that the debug APK installs and that `LauncherActivity` becomes active in a cloud Android 9 emulator. It does not prove that the Resource packs button works through a real tap, that Microsoft sign-in succeeds, that a Minecraft version downloads fully, that the game enters a world, or that rendering/controllers/performance work on a physical phone or Chromebook. These still require interactive device/gameplay acceptance tests.

The launcher now has profile-specific **Mods folder** and **Resource packs** shortcuts in portrait and landscape layouts. They open each selected profile's `mods/` and `resourcepacks/` directories, creating a directory when it does not exist; these shortcuts provide manual file access, not full mod/resource-pack installation, activation, conflict detection, or compatibility checking.

The source patcher is regression-tested and the APK from the verified feature-branch run is signature-verified by CI. The project still builds the upstream Amethyst engine pinned at `330c6eae3164df64bdc4828e946a9e62cc5169e4`; myLauncher is a customization of that engine, not a replacement Java VM or game engine. Review [FEATURE_ROADMAP.md](FEATURE_ROADMAP.md) for which other feature groups remain inherited from upstream, partial, or planned.

### Missing-version metadata recovery

If the launcher shows `FileNotFoundException` for `versions/<version>/<version>.json`, or reports that version metadata is missing, unreadable, or empty, treat that Minecraft installation as incomplete.

1. Connect to a working internet connection, return to myLauncher, choose the same version, and press **Play** again. When online, the launcher is patched to download public version files even if the selected profile is local/offline.
2. Leave the launcher open while downloads finish. Do not create an empty JSON file manually.
3. If the error repeats, record the selected version ID and the relevant launcher log lines. Do not share Microsoft tokens or other account secrets.
4. Without internet, use a version whose metadata, client, libraries, and assets are already installed. Creating a local profile alone does not install Minecraft.

## Manual device smoke test

After a debug APK is available on the target Android 9 device:

1. Install the APK and open **Add account → Local account**.
2. Create a unique username with 3–16 letters, digits, or underscores and confirm it appears in the account spinner.
3. Select a lightweight Minecraft version (for example 1.8.9); while connected to the internet, let the launcher download it if needed, then start the game and test a single-player world.
4. Return to the launcher and verify that **Open game directory** works with only the local profile.
5. Verify the Microsoft account button still opens Microsoft sign-in. Tap **Mods folder** and **Resource packs** and confirm each opens the selected profile's `mods/` and `resourcepacks/` directories. These actions have source/regression and launcher-start CI coverage, but their taps still require interactive acceptance testing. An actual device test is still required for memory pressure, rendering, and gameplay stability.

## Upstream and licensing

This project builds and customizes [AngelAuraMC/Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android), which is based on PojavLauncher. Retain and follow the upstream license and notices when building or redistributing the APK. This repository provides the customization script so changes can be reviewed and reproduced.