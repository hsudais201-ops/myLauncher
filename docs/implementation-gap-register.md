# Flame Launcher implementation gap register

Audit baseline: `feat/flame-jvm-dependency-preload` (commit `a6dcad669e94c4344725647eadaed2e74701d7ff`).

This register is based on the repository tree, project README, embedded-JVM design, launch/runtime Kotlin contracts, native loader/probe, Android manifest, Gradle configuration, and CI workflow. It distinguishes implemented foundations from missing production behavior. A green unit-test/APK build is not evidence of a playable Minecraft client.

## Verified foundations

- Android/Compose application shell and navigation.
- Modrinth and CurseForge catalog/provider foundations, content compatibility planning, dependency resolution, staged downloads, integrity checks, transactional installs, manifests, and modpack handling.
- Mojang version metadata validation and bounded artifact download/install coordinators for client JAR, libraries, natives, asset index and assets.
- Signed Mojo runtime package download, extraction, validation and transactional installation foundations.
- JVM ELF/dependency inspection, native library preflight/retention, launch argument parsing, VM option planning, process-session state model, and embedded/process host routing contracts.
- OpenGL ES diagnostic surface.
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
| P1 | Real game profiles | Persistent per-instance Minecraft version, loader, Java runtime, memory, resolution, JVM/game arguments, content target and isolation. | Create/clone/edit/delete profile; profile-specific launch and content isolation tests. |
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

1. Add a native bridge smoke-test target and a strict device/ABI support gate. Do not enable game launch yet.
2. Implement VM creation and a tiny Java main smoke test for one supported Android ABI/device, including output and safe session termination.
3. Wire the bridge to the existing process/session interfaces only after Gate 2 passes.
4. Complete one end-to-end vanilla Minecraft installation and launch path before adding mod-loader complexity.
5. Implement and validate the Android LWJGL backend; then test title screen, input, audio, pause/resume and stop.
6. Add Microsoft/offline account flows and persistent game instances.
7. Complete service-backed Feature Store, Social Hub, ServerState and Flame AI independently.
8. Expand the device/ABI compatibility matrix only when physical-device evidence exists.

## Release gates

- Gate A — CI: unit tests and debug APK build pass.
- Gate B — install: verified runtime and all game artifacts install on a physical device.
- Gate C — VM: embedded JVM runs a Java smoke-test main class safely alongside Android ART.
- Gate D — graphics: Android LWJGL backend renders and handles lifecycle/input/audio.
- Gate E — game: legitimate Minecraft client reaches the title screen and a world, with controls.
- Gate F — stability: stop/restart, cancellation, low-memory, crash recovery and each advertised ABI are verified.

Current evidence from the baseline establishes Gate A only. Gates B–F are not established by the current repository tests or APK build.
