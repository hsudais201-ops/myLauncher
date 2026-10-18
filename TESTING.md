# VoidCraft Launcher Testing Guide

## Test layers

### 1. Fast regression checks

Run from the repository root on Linux with Python 3:

    python3 -m py_compile .github/scripts/customize_amethyst.py .github/scripts/apply_logo.py .github/scripts/patch_renderpearl_workaround.py .github/scripts/test_customizer.py .github/scripts/test_renderpearl_workaround.py .github/scripts/customize_ui.py .github/scripts/test_ui_customizer.py
    sh -n .github/scripts/prepare_emulator_kvm.sh
    sh -n .github/scripts/emulator_smoke_test.sh
    python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v

These test project customization scripts without requiring a connected Android device. The Python tests use fixture source trees in important places; a passing unit suite proves the tested contract, not every interaction against upstream source.

### 2. Full CI build/package/emulator validation

Use the GitHub Actions tab and run **Build VoidCraft Launcher APK** manually, or trigger the workflow by changing .github/workflows/build.yml or .github/scripts/** in a branch/PR.

The current workflow performs:
1. Python compilation.
2. Shell syntax validation.
3. Project unittest regression suite.
4. Java 21 and Android SDK/NDK setup.
5. Fetch and verify the pinned Amethyst-Android revision and submodules.
6. Apply the customization, renderer workaround, UI theme and icon.
7. Run diff whitespace checks.
8. Build :jre_lwjgl3glfw:build and :app_pojavlauncher:assembleDebug.
9. Verify APK presence, signing, package ID/name and minimum SDK; generate SHA-256 sidecar.
10. Install/start the APK on an Android API 28 x86_64 emulator and collect diagnostics.
11. Upload APK, checksum, build details and emulator logs/screenshots.

The smoke script confirms launcher activity startup/foreground and diagnostics. It does not prove that Minecraft itself launched, a world loaded, account authentication succeeded, downloads completed, or the rendering/input stack works on a physical device.

## Required test additions as features are implemented

- **Version metadata:** malformed, empty, unreadable and missing files; inherited parent manifest; unsupported version; retry and repair behavior.
- **Downloads:** retry/backoff, partial files, cancel/cleanup, hash mismatch, duplicate suppression, network loss, paused/resumed state and process restart.
- **Runtime/launch:** Java-version selection, ABI/native library mismatch, memory range validation, argument construction, duplicate process prevention, process termination and crash redaction.
- **Authentication:** expired session, account switch/logout, network error, entitlement failure and proof that no tokens appear in logs/exports.
- **Instances:** create/clone/import/export/rename/delete collisions, separate mod/config/world paths, backup before update and no-overwrite restore.
- **Archives:** path traversal, absolute paths, duplicate normalized names, file/parent conflicts, symlink escapes where applicable, huge entry count, oversized uncompressed payload, CRC/size mismatch and disk exhaustion.
- **Mods/modpacks:** provider metadata mismatch, unsupported loader/game version, missing dependency, side mismatch, restricted file, invalid manifest, malicious archive paths, partial install and rollback.
- **Settings:** schema migration, unknown keys, wrong types, numeric bounds, storage persistence, import confirmation and exclusion of identity/session/world data.
- **Logs/sharing:** token patterns, bearer headers, large files, empty/missing log, diagnostic truncation and share-sheet review.
- **Android lifecycle:** rotation, background/foreground, process kill, low memory, denied storage/notification permission, low disk, network disconnect and notification cancellation.

## Device acceptance matrix

Track each row as **not run**, **pass**, **fail**, or **blocked**, with device model, Android version, APK commit/artifact, Minecraft version, runtime and renderer recorded.

| Scenario | Current evidence boundary | Required acceptance |
|---|---|---|
| API 28 x86_64 emulator installs and foregrounds launcher | Covered by CI smoke script/workflow | Keep automated |
| ARM64 physical Android phone | Not proven by x86_64 emulator | Install, sign in, install/launch a supported version, load a world |
| ARM32 or x86 physical device | Support not established | First audit bundled ABIs and runtime availability; test only if claimed |
| Low-memory device | Low-memory defaults exist; actual gameplay not guaranteed | Launch and load world under measured memory pressure; record crash/thermal outcomes |
| Version install/repair | Source/unit coverage exists; full device download unverified | Interrupt/retry, verify final files, repair safely |
| Microsoft sign-in and refresh | Upstream flow exists; end-to-end acceptance unverified | Test permitted account, expiry, switch and logout; confirm secrets are redacted |
| Actual Minecraft world launch | Not proven by launcher-only smoke test | Enter/exit single-player world and repeat after app rotation/backgrounding |
| Touch layouts and controls | Upstream input system exists; full custom workflow not verified | Test actual movement, camera, hotbar, layout save/restore |
| External keyboard/mouse/gamepad | Not established by emulator startup | Test each device only when supported by input integration |
| Mods/loaders/modpacks | Folder toggles are narrower than installers | Test named supported combinations and safe failure cases |
| Worlds/backups | ZIP backup/restore/verify logic is source-regression covered | Test real worlds, conflicts, low storage, cancellation and restore integrity |
| Storage/notification permissions | Full matrix not established | Deny and regrant permissions; confirm clear errors and no data loss |
| Tablet/Chromebook/rotation/accessibility | Responsive customizations exist; device acceptance incomplete | Test large layouts, focus/labels, display scaling, mouse/keyboard and landscape |

## Result reporting

For each run, record:
- Branch and commit SHA.
- Test command or workflow URL.
- Exit/result status and failed step.
- APK artifact and checksum when produced.
- Emulator/device, Android version and architecture.
- Minecraft version, Java runtime and renderer for gameplay tests.
- Exact limitations or blocked scenarios.

Never write “all tests passed” unless the complete referenced run shows that. Never say “Minecraft launches” based only on APK build success or launcher activity startup.
