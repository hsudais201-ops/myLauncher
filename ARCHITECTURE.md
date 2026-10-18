# VoidCraft Launcher Architecture

## Status and scope

VoidCraft Launcher is not a standalone Minecraft engine written from scratch. This repository holds project customization scripts, tests and build automation around a pinned upstream Amethyst-Android checkout. The workflow obtains upstream source at build time, applies project patches, then builds the Android APK. The actual app/runtime implementation is substantially inherited from that upstream project.

## High-level structure

- README.md — project overview, build process, user-facing limitations and troubleshooting.
- FEATURE_ROADMAP.md — broad feature inventory and evolving implementation evidence.
- MASTER_MOBILE_LAUNCHER_SPEC.md — full mobile feature and safety requirements.
- .github/workflows/build.yml — repeatable regression/build/package/emulator workflow.
- .github/scripts/customize_amethyst.py — deterministic launcher branding and behavior patches applied to upstream app source.
- .github/scripts/patch_renderpearl_workaround.py — narrowly version-gated MobileGlues/RenderPearl compatibility adjustment.
- .github/scripts/customize_ui.py — VoidCraft night-violet UI customization.
- .github/scripts/apply_logo.py — vector app-icon application and validation.
- .github/scripts/emulator_smoke_test.sh — installs APK, starts launcher activity and records emulator diagnostics/screenshots.
- .github/scripts/prepare_emulator_kvm.sh — hosted runner emulator/KVM setup.
- .github/scripts/test_*.py — regression coverage for patcher behavior, UI customization, XML, safe file operations and expected build/smoke-test properties.
- upstream/ — generated/temporary checkout populated by the workflow; excluded from version control and not the primary source tree.

## Toolchain and upstream pin

The build workflow currently:
- Uses Ubuntu 24.04 hosted runner.
- Runs Python 3 compilation, shell syntax checks and unittest discovery before expensive toolchain setup.
- Uses Temurin Java 21 for Gradle.
- Installs Android SDK compile platform android-37.0 and NDK 27.3.13750724. At the pinned upstream revision, app_pojavlauncher/build.gradle declares minSdkVersion 21 and targetSdkVersion 34; the README's intended supported-device target is Android 9/API 28 and newer, and CI verifies the APK minimum SDK is no greater than 28.
- app_pojavlauncher/src/main/jni/Application.mk declares native build ABIs armeabi-v7a, arm64-v8a, x86 and x86_64. This declaration is not sufficient proof that the complete Java runtime, graphics stack, and native dependencies work on all four architectures; only the x86_64 API 28 emulator is used by current automated smoke testing.
- Fetches AngelAuraMC/Amethyst-Android at commit 330c6eae3164df64bdc4828e946a9e62cc5169e4 and initializes pinned submodules.
- Applies VoidCraft patches and checks diffs for whitespace errors.
- Builds :jre_lwjgl3glfw:build and :app_pojavlauncher:assembleDebug.
- Verifies APK presence, signature, package label/ID and that minimum SDK is API 28 or lower.
- Creates a SHA-256 sidecar.
- Installs and launches the APK in an Android API 28 x86_64 emulator and uploads artifacts/diagnostics.

Do not update the upstream pin casually. A pin change must be reviewed against patcher assumptions, submodules, build behavior, tests, native dependencies and user data format.

## Runtime boundaries

Amethyst-Android supplies the launcher/game execution architecture, Java runtime-related components, native graphics/input integration, account flow, version/download infrastructure, loaders and other inherited functions. VoidCraft adds targeted branding, low-memory defaults, account-flow adjustments for local profiles, selected-profile folders and safe operations, metadata repair support, logging/settings utilities, backup handling and a guarded renderer workaround.

The precise Java versions, Minecraft releases, renderers and ABIs that work depend on upstream runtime artifacts, native libraries and device/driver capabilities. A Java/runtime class existing in upstream source is not proof of a compatible downloadable runtime on every architecture. A successful x86_64 emulator launch of the launcher is not proof of gameplay or ARM64 support.

## Build/data flow

1. CI checks out this repository on main or a PR branch.
2. Python regression tests and shell syntax checks validate the customization scripts.
3. CI fetches the exact upstream commit and recursively checks out submodules.
4. Patcher scripts modify expected upstream files and reject unexpected source drift where guards apply.
5. Gradle builds the required native/GLFW support and debug Android APK.
6. CI checks the APK, creates a checksum sidecar, installs it into Android API 28 x86_64 emulator, starts the launcher activity and collects diagnostics.
7. CI uploads APK, checksum, build information and emulator diagnostics.

The build workflow is configured for pushes to main when .github/workflows/build.yml or .github/scripts/** change, for pull requests with those path changes, and for manual workflow dispatch. A docs-only PR may not trigger this workflow automatically; run a manual workflow when a full baseline validation is required.

## Manifest and permission baseline

At the pinned upstream source revision, the app manifest declares OpenGL ES 2.0 and marks PC hardware as optional. Declared permissions include INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, RECORD_AUDIO, VIBRATE, foreground-service/data-sync/special-use permissions, and READ/WRITE_EXTERNAL_STORAGE limited to API 28 and lower. The application also has android:allowBackup=true. TestStorageActivity and ImportControlActivity are exported, and the scoped FolderProvider is exported with a MANAGE_DOCUMENTS permission plus URI-grant support. Confirm these declarations against the generated/merged manifest and review data backup, entry-point intent handling, provider path confinement and permission boundaries before release; this source inspection alone does not establish a vulnerability or prove installed behavior.

## Data and security boundaries

- Keep all profile/world/config operations confined to the selected profile or user-authorized path.
- Use Android storage APIs consistent with the active OS, scoped-storage rules and user-granted URI access.
- Treat provider manifests, ZIPs, mod JARs, resource packs, shader packs and imported configuration as untrusted input.
- Validate path normalization, destination collisions, archive size/entry bounds and content integrity before final placement.
- Stage risky writes, then move into final destinations only after validation.
- Never overwrite newer worlds/configuration silently; use collision handling and explicit confirmation.
- Keep authentication tokens, refresh tokens, client secrets, authorization headers and private account data out of logs and portable settings exports.
- Keep local/offline profile semantics distinct from Microsoft/Minecraft account authentication and entitlement.
- Request only necessary Android permissions. Optional analytics/cloud/crash sharing requires explicit consent and clear data behavior.

## Current verification boundary

Source patcher/regression coverage validates many custom behaviors in isolation. The CI job also compiles and packages the Android app and checks launcher foreground startup in an API 28 x86_64 emulator. It does not validate every menu by tap, Microsoft sign-in, a full Minecraft download, creating/loading a world, native graphics correctness, physical-device performance, all ABIs, gamepad behavior or Chromebook compatibility.

## Architecture questions still open

- Confirm target/minimum SDK and source manifest values against the generated APK and final merged manifest.
- Verify which runtime/graphics/native library files are packaged for each declared ABI and run supported-device/gameplay acceptance beyond the x86_64 emulator.
- Which Java runtimes and Minecraft/loader combinations are actually distributable and usable per ABI.
- Whether download jobs resume correctly after process death and what state is persisted.
- Complete account token storage/refresh/logout implementation and threat boundaries.
- Actual profile/instance directory data model and version compatibility.
- Test access to physical ARM64 devices and real gameplay acceptance.
- Whether lint/static analysis can be added without modifying the upstream pin or incurring excessive build cost.

Answer these by inspecting the pinned upstream files and generated APK; do not infer them from desktop launcher conventions.
