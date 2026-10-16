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
- An offline profile does not supply game files or bypass Minecraft licensing. When internet is available, myLauncher can download public Minecraft version metadata, libraries, client files, and assets for a local profile without Microsoft sign-in. Without internet, the selected version must already be fully installed.
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

As of 2026-10-16, the blocker is private-repository GitHub Actions runner availability, not a demonstrated source-code error. Diagnostic run [38881165972](https://github.com/hsudais201-ops/myLauncher/actions/runs/38881165972) tested `ubuntu-slim`, `ubuntu-22.04`, `ubuntu-24.04`, `ubuntu-latest`, `windows-latest`, and `macos-15`. All six jobs failed before any step ran: the job records had no assigned runner and empty step lists, and log downloads returned `BlobNotFound`. The same no-runner symptom appears in private [Droidlauncher](https://github.com/hsudais201-ops/Droidlauncher/actions/runs/38787592198). In contrast, a run in the public [mycraftlauncher-](https://github.com/hsudais201-ops/mycraftlauncher-/actions/runs/38788571336) repository provisioned a runner and reached its unit-test/build step before failing within that project’s build.

This strongly points to private-repository Actions eligibility, usage, or billing settings rather than a bad runner label or this customization code. GitHub’s [Actions billing documentation](https://docs.github.com/en/billing/concepts/product-billing/github-actions) says private repositories use an account-level included-minutes quota, and usage is blocked after that quota is used when there is no valid payment method. I cannot view this account’s private billing dashboard from the repository connection, so quota exhaustion is suspected, not confirmed. Check [Billing and licensing](https://github.com/settings/billing/summary) for Actions usage, any budget configured to stop usage, and payment-method status; also confirm Actions are permitted under repository **Settings → Actions → General**. If billing and permissions are clear but the private jobs still fail before runner assignment, [contact GitHub Support](https://support.github.com/) and provide run `38881165972`, job IDs `116907965829`, `116907966111`, `116907966184`, `116907966237`, `116907966335`, and `116907966365`, plus private Droidlauncher run `38787592198`. Only GitHub can inspect the service-side reason for jobs that never receive a runner.

The build workflow now runs on pushes to `main` when `.github/workflows/build.yml` or `.github/scripts/**` changes, on matching pull requests, and when manually dispatched from **Actions → Build myLauncher APK → Run workflow**. Subsequent build runs [38890724054](https://github.com/hsudais201-ops/myLauncher/actions/runs/38890724054), [38890924422](https://github.com/hsudais201-ops/myLauncher/actions/runs/38890924422), and [38890957304](https://github.com/hsudais201-ops/myLauncher/actions/runs/38890957304) also failed before any step ran; the job records had empty step lists, and log downloads returned `BlobNotFound`. The runner diagnostic remains manual-only and uses one Ubuntu runner to avoid starting a multi-OS matrix on every workflow edit.

Source review confirmed 22 expected file/patch anchors in the pinned Amethyst commit, and an in-memory patch simulation passed 10 checks for branding, Android 9 minSdk, both launcher layouts, local-account flow, account shortcut, low-memory defaults, and manifest icon references. The customizer updates the shared `Tools.APP_NAME` constant, with a regression assertion. The APK checksum-delivery workflow also includes regression assertions. The full Python regression suite has **not** executed successfully in GitHub Actions because the private jobs fail before steps start. None of the source-level checks is an Android compilation, APK-signature verification, or device test. No verified APK has been produced. After runner access is restored, run the Python regression suite, build and verify the APK, then test Microsoft sign-in, asset downloads, rendering, heat, memory pressure, and FPS on the target device.

## Manual device smoke test

After a debug APK is available on the target Android 9 device:

1. Install the APK and open **Add account → Local account**.
2. Create a unique username with 3–16 letters, digits, or underscores and confirm it appears in the account spinner.
3. Select a lightweight Minecraft version (for example 1.8.9); while connected to the internet, let the launcher download it if needed, then start the game and test a single-player world.
4. Return to the launcher and verify that **Open game directory** works with only the local profile.
5. Verify the Microsoft account button still opens Microsoft sign-in. An actual device test is still required for memory pressure, rendering, and gameplay stability.

## Upstream and licensing

This project builds and customizes [AngelAuraMC/Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android), which is based on PojavLauncher. Retain and follow the upstream license and notices when building or redistributing the APK. This repository provides the customization script so changes can be reviewed and reproduced.