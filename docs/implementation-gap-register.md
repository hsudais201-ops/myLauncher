# Flame Launcher implementation gap register

Audit baseline: `feat/flame-jvm-dependency-preload` (commit `a6dcad669e94c4344725647eadaed2e74701d7ff`).

This register is based on the repository tree, project README, embedded-JVM design, launch/runtime Kotlin contracts, native loader/probe, Android manifest, Gradle configuration, and CI workflow. It distinguishes implemented foundations from missing production behavior. A green unit-test/APK build is not evidence of a playable Minecraft client.

## Verified foundations

- Android/Compose application shell and navigation.
- Modrinth and CurseForge catalog/provider foundations, content compatibility planning, dependency resolution, staged downloads, integrity checks, transactional installs, manifests, and modpack handling.
- Mojang version metadata validation and bounded artifact download/install coordinators for client JAR, libraries, natives, asset index and assets.
- Signed Mojo runtime package download, extraction, validation and transactional installation foundations.
- JVM ELF/dependency inspection, including rejection of cyclic dependency graphs, native library linker preflight/retention code, launch argument parsing, VM option planning, process-session state model, and embedded/process host routing contracts. Static dependency-resolution tests and injected preflight tests do not substitute for executing the linker checks on a physical device.
- OpenGL ES diagnostic surface.
- Safe per-instance profile create/list/select/rename/delete with isolated content directories and atomic metadata/selection writes.
- Per-instance launch preference persistence for memory bounds, resolution, fullscreen, JVM/game argument text, and an optional preferred Java runtime ID, with validation and profile-path checks.
- Per-profile Java runtime preference resolution with exact Java-major and device-architecture matching; explicit selections fail closed rather than silently falling back.
- Launch-readiness preflight that checks profile/plan version agreement, settings validity, Mojang Java-major metadata, and installed compatible runtime selection. It reports typed blocking reasons.
- Installed-artifact integrity validation that checks every planned client/library/native archive/asset-index file and asset object for safe in-root paths, exact size, and SHA-1. It reports missing and corrupt files separately.
- Native extraction completeness validation that compares the promoted native directory against the extractor's exact output set, rejects missing/unexpected files and symlinks, and records extracted-file SHA-1 hashes. Extracted `.so` files are checked for complete ELF32/ELF64 header length, class, byte order, identification/header version, ET_DYN type, declared header size, and machine type against the configured device architecture. This structural header gate does not prove DT_NEEDED dependency closure, symbol availability, or successful Android linker loading.
- Device-RAM-aware conservative heap recommendations (including a 1.5 GB maximum recommendation for a 4 GB device) and translation of saved profile preferences into validated, shell-free JVM/game launch arguments. Launcher-owned paths, classpath, memory flags, display flags and authentication arguments cannot be overridden by profile text.
- Unit-test suite and CI debug APK build.

## Blocking gaps (must be implemented before claiming playable)

| Priority | Gap | Required implementation | Acceptance evidence |
|---|---|---|---|
| P0 | Native AndroidJvmBridge | Implement JNI VM lifecycle against the exact supported Mojo/OpenJDK builds; ART coexistence, JNI version negotiation, controlled exit/abort hooks, thread attach/detach, global/local reference ownership, exception propagation, output capture and session handle. | Device smoke test creates one VM, runs a Java main class, reports output, exits the game session without terminating Android, and survives repeated UI lifecycle events. |
| P0 | Bridge wiring | Supply a production bridge to EmbeddedJvmProcessHost and connect runtime preparation, installed game artifacts, launch plan, process controller and Play UI. | User can select a supported profile, see preflight stages, start/cancel/stop, and receive accurate terminal state and diagnostics. |
| P0 | LWJGL compatibility | Implement or integrate a licensed Android-compatible LWJGL backend and native loading hooks for the exact Minecraft/LWJGL versions supported. | EGL/OpenGL context, game window/display, input, audio, resize, pause/resume and teardown verified on physical devices. |
| P0 | Complete game installation | Join metadata, dependency plan, client JAR, libraries, native extraction, asset index/objects and per-instance configuration into one resumable installation flow. | Fresh profile installs offline from verified cache and passes launch-time completeness checks; interrupted install resumes or rolls back safely. |
| P0 | Device validation | Test the complete runtime install, VM smoke test and game launch on Android 8+ devices/ABIs actually claimed as supported. | Published compatibility matrix records device/API/ABI/JDK build, VM creation, renderer, input, audio, pause/resume, stop and crash behavior. |

## Important product gaps

| Priority | Gap | Required implementation | Acceptance evidence |
|---|---|---|---|
| P1 | Accounts | Microsoft OAuth/device-code flow, token storage/refresh/revocation, entitlement/profile retrieval, and compliant offline profile support. Never log or expose tokens. | Auth success, cancellation, expired-token recovery, logout and offline behavior tested. |
| P1 | Complete real game profiles | Complete game installation and launch integration remain unimplemented. Installed-artifact integrity validation is implemented as a standalone check and is not yet wired into the Play flow. Native extraction is gated by a post-extraction file-set validator and ELF architecture check inside the native install coordinator. JVM DT_NEEDED closure resolution rejects unsafe names, missing/ambiguous files and cycles; native code validates ABI-compatible ELF headers, attempts dependency-first dlopen preflight, and checks JNI invocation symbols. These native checks have not yet been demonstrated on the target Chromebook or proven safe for VM creation alongside ART. Profile create/list/select/rename/delete/clone, settings persistence, per-profile Java runtime preference and ABI/major validation, readiness preflight, device-RAM heap recommendations, and validated command-builder translation are implemented; the Play screen still does not start a selected profile. | Profile selection, launch-plan and content-isolation tests, followed by physical-device validation. |
| P1 | Loader-aware content lifecycle | Verify provider metadata, loader/version compatibility, required/optional dependencies, updates, uninstall and rollback across profiles. | Modrinth/CurseForge install/update/remove tests against real fixture packages and profile state. |
| P1 | CurseForge credential protection | Replace client-held API-key model with a production backend proxy and rate limiting/abuse controls. | No provider secret in APK, logs, source or client network requests. |
| P1 | Runtime certificate maintenance | Establish documented upstream certificate rotation, overlap period, expiry monitoring and rollback-safe trust updates. | Rotation test accepts new key, rejects unknown key, and handles old/new overlap. |
| P1 | Resource controls | Detect device RAM/ABI/API/storage, calculate conservative heap limits, configure resolution/render distance and provide low-memory behavior. | Tested on low-memory Android hardware; no unsupported performance claims. |

## Service areas currently represented as planned UI

- Feature Store: catalog, package signing, install/update/uninstall, permission model and service.
- Social Hub: backend, identity, privacy controls, moderation and messaging.
- ServerState: live server data source, refresh/error handling and server status validation.
- Flame AI: real crash/log ingestion, evidence-based diagnosis, explicit approval for repairs, backups and verification.
- Settings: persistent preferences, runtime/profile selection, input layouts, performance and graphics compatibility settings.

These should remain clearly marked as planned until each has a real service and tests.

## Security and reliability review gates

- Keep downloads HTTPS-only with explicit host allowlists, redirect policy, byte/time limits, hashes/signatures, and temporary-file promotion.
- Reject ZIP/TAR traversal, unsafe symlinks, duplicate outputs, decompression bombs and unexpected archive contents.
- Keep secrets out of logs, crash reports, URLs, analytics and source control.
- Ensure cancellation closes streams and removes partial staging data without damaging the last known-good install.
- Ensure install/update/uninstall operations are transactional and recover after process death.
- Ensure UI work is lifecycle-aware; long downloads, extraction, dependency resolution and linker operations run off the main thread.
- Verify all external provider responses as untrusted input.
- Keep native code ABI-gated, JNI exceptions checked, JNI references/thread ownership explicit, and JVM handles retained for process lifetime once VM startup is possible.

## Recommended implementation order

1. Freeze a versioned Mojo/OpenJDK integration target and audit the upstream native bridge, Java runner, class-loader hooks, linker hints, and renderer loader as one system. Preserve LGPL-3.0 and file-level notices before adapting source.
2. Add native smoke-test targets and a strict device/API/ABI support gate. Do not enable game launch yet.
3. Port the VM lifecycle and Java-version-specific native-library hooks, then run a Java main smoke test on one supported physical device, including output and safe launcher-session termination.
4. Port the Android LWJGL native loader and one renderer backend; validate EGL/OpenGL, resize, input, pause/resume, and teardown before wiring a game session.
5. Connect profile preferences, verified client/libraries/natives/assets, runtime selection, account data, and process/session control into a fail-closed vanilla launch pipeline.
6. Add compliant Microsoft/offline account flows and profile cloning; then expand loader compatibility.
7. Complete service-backed Feature Store, Social Hub, ServerState and Flame AI independently.
8. Expand the device/ABI compatibility matrix only when physical-device evidence exists.

## Release gates

- Gate A — CI: unit tests and debug APK build pass.
- Gate B — install: verified runtime and all game artifacts install on a physical device.
- Gate C — VM: embedded JVM runs a Java smoke-test main class safely alongside Android ART.
- Gate D — graphics: Android LWJGL backend renders and handles lifecycle/input/audio.
- Gate E — game: legitimate Minecraft client reaches the title screen and a world, with controls.
- Gate F — stability: stop/restart, cancellation, low-memory, crash recovery and each advertised ABI are verified.

Current evidence establishes profile/content foundations, runtime selection/readiness preflight, native extraction file-set validation, strict ELF header validation, static JVM dependency-closure resolution, and Gate A (unit tests and debug APK build). CI run #584 passed on commit 427f6d8a3fa32bb610e61f7ed1902096d7e308d3; CI run #585 passed on commit b26e8ea916b59b002875d94e2f780f3e00262289, including cyclic dependency rejection tests. Commit b296fe3129e52573f6fcd580bbfd34938203ec93 moves staged native-file validation before promotion and adds a rollback regression test proving a failed validation preserves the previous native directory; CI run #588 passed, including unit tests, debug APK assembly and artifact upload (artifact: `flame-launcher-debug-apk`, 21,010,043 bytes; expires 2026-11-26). Native linker preflight code exists but has not been demonstrated on the target Chromebook; no embedded VM creation, LWJGL game rendering, or physical-device launch has been proven. The readiness preflight does not verify downloaded artifact completeness. Gates B–F are not established by the current repository tests or APK build.
