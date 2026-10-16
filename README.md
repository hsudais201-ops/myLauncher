# myLauncher

A personal Minecraft: Java Edition launcher build for Android, customized for low-memory devices.

**Runtime engine:** [Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android). This repository stores the myLauncher build customization and automation. It does not reimplement a Java virtual machine, OpenGL/LWJGL, account sign-in, game downloads, and mod loaders from scratch; those core runtime features come from the upstream open-source engine.

## Device target

- Android 9 (API 28) and newer. Upstream Amethyst declares a lower minimum Android SDK, so this build does not raise it.
- Intended for testing on devices with 2 GB RAM or less; performance is not guaranteed and varies with CPU, GPU, renderer, version, and free memory.
- Built-in low-memory defaults: for devices reporting at most 2,048 MB total RAM, the allocator uses 296 MB below 1,024 MB, 448 MB below 1,536 MB, and a reduced 512 MB heap from 1,536–2,048 MB. The default resolution scaler targets a 480-pixel short screen edge for that RAM tier when the physical edge is larger than 480 pixels; scaling is rounded upward to the next supported 5% step. Saved preferences still take priority, so users can change the values afterward.
- Suggested Minecraft settings: use 1.8.9 or 1.12.2, render distance 4–6 chunks, Fast graphics, and no shaders. Set these in Minecraft; this project does not force in-game settings.

## Account support and interface

- The launcher retains Amethyst's Microsoft account sign-in and local/offline profile flow. The custom patch removes the online-account gate in the account selector and the local-login screen, so a local profile can be created without first adding a Microsoft account. The local username field is configured as a username field.
- An offline profile is only a local player name; it is **not** a Microsoft-authenticated Minecraft account. Servers requiring authenticated/online-mode accounts may reject it. Do not use offline mode to impersonate another player.
- An offline profile does not supply game files or bypass Minecraft licensing. The required game version/assets must already be present or downloaded, and downloading them may require an internet connection.
- Local profiles can browse their game directory. Some upstream utilities, including the JAR installer, retain the upstream online-account requirement; this customization does not remove those separate restrictions.
- The launcher home screen gets a lightweight myLauncher title/subtitle header plus a dedicated **Add account** shortcut in portrait and landscape. The shortcut opens the existing Microsoft/local account selector.
- The Android launcher icon is replaced by a lightweight vector mark matching the new dark/purple identity. No UI library or significant runtime dependency is added.

## Build the APK

1. Open the Actions tab in this GitHub repository.
2. Run **Build myLauncher APK** using **Run workflow**, or push a change to `main`.
3. Open the completed workflow run and download its **myLauncher-debug-apk** artifact. The artifact includes the APK and a `.sha256` checksum sidecar. Push/PR builds are triggered by changes to `.github/workflows/build.yml` or `.github/scripts/**`; use **Run workflow** for a manual build.
4. Before installing, verify the transfer on Linux with `sha256sum -c <apk-filename>.sha256` from the directory containing the APK and sidecar. Then transfer the APK to Android, install it, and allow installation from your download source if Android asks.

The workflow fetches the exact Amethyst commit `330c6eae3164df64bdc4828e946a9e62cc5169e4` and its submodules, rewrites SSH-style public GitHub submodule URLs to HTTPS for hosted runners, validates submodule checkout status, validates the customization regression suite before installing the Android toolchain, applies the customizations, and builds the debug APK. The source pin is independent of branch movement. Update the pin deliberately when adopting a newer upstream version.

## Files

- `.gitignore` — excludes local upstream checkouts, Python caches, and build outputs.
- `.github/workflows/build.yml` — repeatable Android build workflow, path-filtered to launcher customization/build changes.
- `.github/workflows/runner-diagnostic.yml` — minimal hosted-runner diagnostic workflow.
- `.github/scripts/customize_amethyst.py` — deterministic patch for app branding, package/authority IDs, low-memory defaults, offline account access, launcher header, account shortcut, and vector app icon.
- `.github/scripts/test_customizer.py` — regression tests for branding, offline-account submission and labeling, local game-directory access, both launcher layouts, XML well-formedness, missing-anchor failures, repeat-run idempotency, and CI submodule/APK safeguards.

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
    sdkmanager --install 'platforms;android-37' 'ndk;27.3.13750724'
    python3 .github/scripts/customize_amethyst.py upstream
    cd upstream
    ./gradlew :jre_lwjgl3glfw:build :app_pojavlauncher:assembleDebug

## Verification and limitations

As of 2026-10-16, GitHub Actions runner provisioning is still the immediate build blocker. The latest APK build, [run 38880643713](https://github.com/hsudais201-ops/myLauncher/actions/runs/38880643713) (attempt 2), failed as job `116906996358` with zero steps; fetching its logs returned `BlobNotFound`. The checksum, regression tests, Android build, and artifact upload did not run. The preceding [build run 38880631231](https://github.com/hsudais201-ops/myLauncher/actions/runs/38880631231) and earlier [run 38880101851](https://github.com/hsudais201-ops/myLauncher/actions/runs/38880101851) failed before steps too; log retrieval for run 38880101851 returned `BlobNotFound`. There are no build artifacts from these runs. Across the 89 workflow runs currently visible in the repository, all 89 have failed.

The diagnostic now tests four runner labels side by side. All four jobs failed before recording steps:
- `ubuntu-24.04`: job `116904025736`
- `ubuntu-slim`: job `116904026064`
- `ubuntu-22.04`: job `116904026081`
- `ubuntu-latest`: job `116904026115`

Because the failure occurred across all tested labels, changing the runner label again is unlikely to be sufficient. GitHub's [status page](https://www.githubstatus.com/) listed no incident for October 16 when checked, so a platform-wide outage is not confirmed; that does not rule out a runner-provisioning or account-specific problem. Check repository **Settings → Actions → General**, account billing/usage and spending limits, and any verification or usage restrictions. If these are clear, [contact GitHub Support](https://support.github.com/) with the run links and job IDs above; only GitHub can inspect runner-side telemetry unavailable in the job logs.

Source review confirmed 22 expected file/patch anchors in the exact pinned Amethyst commit, and an in-memory patch simulation passed 10 checks for branding, Android 9 minSdk, both launcher layouts, local-account flow, account shortcut, low-memory defaults, and manifest icon references. The customizer was subsequently extended to update the shared `Tools.APP_NAME` constant, with a regression assertion. The APK checksum-delivery workflow also now has regression assertions. Neither these new assertions nor the full Python test suite has **actually run** in GitHub Actions, because hosted jobs fail before their first step. A separate local shell-only smoke test passed for checksum creation, `sha256sum -c` verification, and hash formatting, but that does not execute the workflow or validate an actual APK. None of these source-level checks is an Android compilation, APK-signature verification, or device test. No verified APK has been produced. After runner access is restored, run the Python regression suite, build and verify the APK, then test Microsoft sign-in, asset downloads, rendering, heat, memory pressure, and FPS on the target device.

## Manual device smoke test

After a debug APK is available on the target Android 9 device:

1. Install the APK and open **Add account → Local account**.
2. Create a unique username with 3–16 letters, digits, or underscores and confirm it appears in the account spinner.
3. Select a previously installed, lightweight Minecraft version (for example 1.8.9), start the game, and test a single-player world.
4. Return to the launcher and verify that **Open game directory** works with only the local profile.
5. Verify the Microsoft account button still opens Microsoft sign-in. An actual device test is still required for memory pressure, rendering, and gameplay stability.

## Upstream and licensing

This project builds and customizes [AngelAuraMC/Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android), which is based on PojavLauncher. Retain and follow the upstream license and notices when building or redistributing the APK. This repository provides the customization script so changes can be reviewed and reproduced.