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

If the launcher reports `FileNotFoundException` for a path like `versions/<version>/<version>.json` or says the version metadata is missing, the selected installation is incomplete or its metadata could not be read. Modded versions that inherit from a vanilla version also require that parent version's JSON metadata; the launcher now identifies a missing or unreadable base manifest before trying to continue.

1. Connect to a working internet connection, return to VoidCraft Launcher, select the same Minecraft version, and press **Play** again. The launcher should be able to download the missing public version files without requiring Microsoft sign-in just because you use a local profile.

For an explicit check without launching the game, select the profile and press **Verify / repair version** on the launcher home screen. It uses the existing downloader to verify/download files for official versions present in the current version list, requires internet access, and reports success or failure. Modded/custom versions that are not in that list must be repaired with their matching loader tools.
2. Keep the launcher open until downloads finish. Do not create an empty `.json` file manually; it is not valid version metadata.
3. If the error continues, copy the exact version ID and the relevant error from the launcher log. Do not share Microsoft access tokens or other account secrets.
4. When offline, use a version whose metadata, client, libraries, and assets are already installed. Creating a local profile alone does not install Minecraft files.
- Local profiles can browse their game directory. Some upstream utilities, including the JAR installer, retain the upstream online-account requirement; this customization does not remove those separate restrictions.
- The launcher home screen gets a lightweight VoidCraft Launcher title/subtitle header plus a dedicated **Add account** shortcut in portrait and landscape. The shortcut opens the existing Microsoft/local account selector. Profile shortcuts expose Mods, Resource packs, World saves, Game logs, Screenshots, Crash reports, Config, and Shader packs.
- The app name is normalized across localized Android string resources, and the in-app **About** text identifies the VoidCraft Launcher custom build and MF while retaining upstream and third-party license credits.
- Long-press **Mods folder** to see top-level `.jar` and `.jar.disabled` files and enable or disable one at a time. Each rename is confirmed, demo profiles remain protected, and a conflicting destination filename is refused. Normal tap still opens the selected profile's Mods folder. This lightweight toggle does not install mods or check compatibility.
- The Game logs shortcut opens the selected profile's `logs/` directory, creating it if needed. Long-press **Game logs** to choose **Copy latest log**, **Share latest log**, or **Diagnose latest log**. All actions read at most the final 1 MiB of `latest.log`. Copy/share redact common access-token, refresh-token, client-secret, authorization, Minecraft/XSTS-token, and Bearer patterns; sharing opens Android's chooser for review. Diagnosis classifies common memory, Java, renderer, missing-file, mod, network, and storage errors without showing raw log content, and is best-effort rather than a guaranteed root-cause finding.
- The built-in live logger now supports case-insensitive substring filtering and a clear-filter button. It retains up to 300,000 characters in memory, shows match counts, and marks when older live lines were dropped to prevent unbounded growth. This only filters the in-app live view; it does not rewrite `latest.log`.
- Long-press **Resource packs** to see top-level `.zip` and `.zip.disabled` packs and enable or disable them one at a time. Each rename is confirmed, demo profiles remain protected, and a conflicting destination filename is refused. Normal tap still opens the selected profile's `resourcepacks/` directory. This toggle does not install packs or validate compatibility.
- Long-press **Shader packs** to enable or disable top-level `.zip` and `.zip.disabled` shader packs. Each rename requires confirmation, is confined to the selected profile's `shaderpacks/` directory, and refuses conflicting destination names. Normal tap still opens the folder; this toggle does not install shaders or check GPU compatibility.
- The Screenshots shortcut opens the selected profile's `screenshots/` directory, creating it if needed, so screenshots can be browsed and copied without navigating through the entire game directory.
- The Crash reports shortcut opens the selected profile's `crash-reports/` directory, creating it if needed. Long-press it to prepare the newest `.txt` or `.log` report for Android's share chooser; shared content is capped at 2 MiB and common token patterns are redacted. Review the report and recipient before sending.
- The Config folder shortcut opens the selected profile's `config/` directory, creating it if needed, so mod settings and other configuration files are easier to locate. Long-press **Config folder** to export a JSON subset of portable launcher preferences through Android's share chooser. Account/session data, selected-profile identity, custom Java arguments, control-file paths, and world data are excluded. The export is for backup/reference; it does not import settings or edit config files.
- The Shader packs shortcut opens the selected profile's `shaderpacks/` directory, creating it if needed. It exposes the folder only and does not install shaders or guarantee compatibility with the selected Minecraft version or renderer.
- **Experimental MobileGlues compatibility:** when MobileGlues is the selected renderer, Minecraft 26.3 stable and snapshots after `26.3-snapshot-1` use partial GL error filtering only if the saved error-filter preference is still the default value. Explicit preference values and other Minecraft versions are unchanged. This workaround may help one RenderPearl startup failure; it is not a guarantee of compatibility and does not fix missing Vulkan capabilities or other upstream graphics failures.
- Tap **World saves** to open the saves folder, restore a ZIP backup, verify a ZIP backup, or delete a ZIP backup after confirmation. Restore, verify, and delete pickers show each backup's filename, readable file size, and last-modified time, sorted newest first. Verification fully reads each archive entry in a background thread so data/CRC errors surface; it rejects unsafe, duplicate, and conflicting paths, caps scans at 250,000 entries and 20 GiB uncompressed, and never extracts, edits, or deletes the selected backup. Restore first extracts into a temporary directory, rejects unsafe or duplicate ZIP paths, and limits extraction to the smaller of 20 GB or the available storage budget while keeping free-space reserve. Restored worlds are moved into `saves/` without overwriting existing names (conflicts receive a `(restored)` suffix). Backup deletion is confined to the selected profile's `backups/` directory and never removes restored worlds. Long-press **World saves** to create a timestamped ZIP backup in the profile's `backups/` directory. Backup, verification, and restore run off the UI thread and discard temporary files on failure.
- Shared launcher controls, text fields, dialogs, and both portrait/landscape home screens use a coordinated night-violet palette with a matching hero panel and custom button/input backgrounds. This is a visual customization only; it does not add a UI library or guarantee gameplay/rendering compatibility.
- The Android launcher icon is replaced by a lightweight vector mark matching the new dark/purple identity. No UI library or significant runtime dependency is added.

## Build the APK

1. Open the Actions tab in this GitHub repository.
2. Build validation runs automatically on pushes to `main` and pull requests when `.github/workflows/build.yml` or `.github/scripts/**` changes. You can also run **Build VoidCraft Launcher APK** manually using **Run workflow** in the Actions tab.
3. Open the completed workflow run and download its **voidcraft-launcher-debug-apk** artifact. The artifact includes the APK and a `.sha256` checksum sidecar.
4. Before installing, verify the transfer on Linux with `sha256sum -c <apk-filename>.sha256` from the directory containing the APK and sidecar. Then transfer the APK to Android, install it, and allow installation from your download source if Android asks.

The workflow fetches the exact Amethyst commit `330c6eae3164df64bdc4828e946a9e62cc5169e4` and its submodules, rewrites SSH-style public GitHub submodule URLs to HTTPS for hosted runners, validates submodule checkout status, runs the customization regression suite before installing the Android toolchain, adds Maven Central to the plugin-management repositories required for Android Gradle Plugin transitive dependencies, applies the launcher customizations, applies the guarded Minecraft 26.3 MobileGlues compatibility workaround, validates and applies the Android vector icon, then builds and verifies the debug APK. The emulator smoke test checks that the launcher is resumed and captures diagnostics/screenshots. The source pin is independent of branch movement; update it deliberately when adopting a newer upstream version.

## Files

- `.gitignore` — excludes local upstream checkouts, Python caches, and build outputs.
- `.github/workflows/build.yml` — repeatable Android build workflow, triggered by relevant pushes to `main`, pull requests, or a manual dispatch.
- `.github/workflows/runner-diagnostic.yml` — minimal hosted-runner diagnostic workflow.
- `.github/scripts/customize_amethyst.py` — deterministic patch for branding/package IDs, low-memory defaults, offline account flow, launcher/folder actions, inherited-version metadata guards, safe log/settings actions, and other launcher behavior.
- `.github/scripts/patch_renderpearl_workaround.py` — applies the guarded Minecraft 26.3 MobileGlues compatibility workaround while preserving explicitly selected renderer settings.
- `.github/scripts/apply_logo.py` — applies and validates the Android vector application icon used by CI.
- `.github/scripts/emulator_smoke_test.sh` — installs the APK, launches the launcher entry point, checks foreground activity state, and captures emulator screenshots and diagnostics.
- `.github/scripts/prepare_emulator_kvm.sh` — verifies and enables KVM access for hosted emulator runs.
- `.github/scripts/test_customizer.py` — regression tests for the source patcher, account validation/submission, launcher actions, XML well-formedness, safe file handling, idempotency, build safeguards, and emulator-smoke expectations.
- `FEATURE_ROADMAP.md` — tracks source-confirmed features separately from planned work and device/gameplay acceptance still needed.

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
    git -C upstream diff --check
    python3 .github/scripts/patch_renderpearl_workaround.py upstream
    git -C upstream diff --check
    python3 .github/scripts/apply_logo.py upstream
    cd upstream
    ./gradlew --no-daemon :jre_lwjgl3glfw:build :app_pojavlauncher:assembleDebug --stacktrace

The debug APK is written to `upstream/app_pojavlauncher/build/outputs/apk/debug/app_pojavlauncher-debug.apk`. CI also generates a SHA-256 checksum sidecar and verifies the APK signature/package metadata before its emulator smoke test.

## Verification and limitations

The most recent post-merge `main` workflow, [run #39106117976](https://github.com/hsudais201-ops/myLauncher/actions/runs/39106117976), passed on October 18, 2026, for commit `ffbaa3c27a1300e6391f0ea885e1adf1c1dbe0da` after the world-backup metadata change was merged. It passed regression tests, pinned Amethyst checkout and submodules, Gradle APK build, APK signature/package/minimum-SDK checks, Android 9/API 28 emulator install-and-launch, and uploads of the APK, SHA-256 sidecar, build information, and emulator diagnostics. The backup-picker filename/size/modified-time display also passed its pre-merge workflow in [PR #51 run #39102041594](https://github.com/hsudais201-ops/myLauncher/actions/runs/39102041594).



The world backup/restore change in [PR #15 workflow run #39052725546](https://github.com/hsudais201-ops/myLauncher/actions/runs/39052725546) passed regression checks, APK build/package/signature validation, and Android 9/API 28 emulator install-and-launch smoke tests. The cloud emulator smoke test verifies installation and that `LauncherActivity` becomes active. It does not prove that every folder button works through a physical tap, Microsoft sign-in succeeds, a Minecraft version downloads fully, a world starts, or rendering/controllers/performance work on a physical phone or Chromebook. Interactive device and gameplay acceptance tests remain necessary.

The shipped folder shortcuts open each selected profile's `mods/`, `resourcepacks/`, and `saves/` directories and create them if missing. The Mods shortcut also supports a long-press toggle for top-level JAR files; this is not a mod installer or conflict/compatibility checker. World saves supports timestamped ZIP backups and guarded restore of a chosen backup. Full mod/resource-pack installation, mod conflict detection, resource-pack activation, scheduled/cloud backups, and cloud sync remain unimplemented.

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
5. Verify the Microsoft account button still opens Microsoft sign-in. Tap **Mods folder** and **Resource packs** and confirm each opens the selected profile's directories. Put a test JAR in `mods/`, long-press **Mods folder**, and verify disabling adds `.disabled`, while enabling removes it. Confirm a pre-existing destination name causes a warning rather than overwrite. These button interactions still need device acceptance testing. An actual device test is still required for memory pressure, rendering, and gameplay stability.

## Upstream and licensing

This project builds and customizes [AngelAuraMC/Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android), which is based on PojavLauncher. Retain and follow the upstream license and notices when building or redistributing the APK. This repository provides the customization script so changes can be reviewed and reproduced.