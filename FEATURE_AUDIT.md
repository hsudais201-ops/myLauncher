# VoidCraft Launcher Feature Audit

**Audit date:** 2026-10-18  
**Repository:** hsudais201-ops/myLauncher  
**Base branch audited:** main  
**Audit method:** Read repository README, FEATURE_ROADMAP.md, build workflow, customization patcher and regression-test sources. This is a source/document audit, not a fresh APK build, emulator run, device test, or gameplay test.

## Status definitions

- **Implemented and verified:** real behavior has relevant test/runtime evidence. State the scope of that evidence; a launcher-start smoke test is not gameplay verification.
- **Partially implemented:** a subset exists; other requested behavior remains absent or unverified.
- **Missing:** no implementation evidence found in this repository audit.
- **Broken:** reproducible failure evidence exists.
- **Blocked by platform/service/permission:** requires a capability or access that is unavailable or restricted.
- **Not applicable:** outside the stated Android launcher scope or not useful for this runtime.

Do not upgrade a status because a screen, label, upstream class, or planned roadmap bullet exists. Upstream components must be validated end-to-end before calling the corresponding user-facing feature complete.

## Repository and runtime baseline

| Item | Current finding | Confidence/status |
|---|---|---|
| Repository | Public GitHub repository hsudais201-ops/myLauncher; default branch main; push/admin permission is available to the connected GitHub integration. | Verified metadata |
| Product | VoidCraft Launcher, a personal Android customization/build of the existing launcher engine. | Verified in README and build workflow |
| App source architecture | This repo primarily contains deterministic Python patch/customization scripts, shell scripts, tests, and GitHub Actions. The app source is fetched from upstream at build time, customized, and compiled. | Verified |
| Engine | AngelAuraMC/Amethyst-Android, pinned in the current workflow at commit 330c6eae3164df64bdc4828e946a9e62cc5169e4. The engine is based on PojavLauncher. | Verified workflow/README |
| Android build | Gradle; Java 21 build JDK; compile SDK 37; NDK 27.3.13750724. Upstream app Gradle configuration specifies minSdkVersion 21 and targetSdkVersion 34. The product README documents Android 9/API 28+ as its intended device target, and CI checks APK min SDK is no greater than 28. | Source verified at pinned upstream revision; generated APK still needs per-build confirmation |
| CPU/native ABIs | Pinned upstream Application.mk specifies armeabi-v7a, arm64-v8a, x86 and x86_64 for NDK builds. The CI smoke emulator is x86_64. This source declaration does not prove compatible Java runtimes and all required graphics/native dependencies are present for every packaged ABI. | Native ABI intent verified; end-to-end device support unverified |
| Manifest/permissions | Pinned upstream manifest declares OpenGL ES 2.0 and optional PC hardware; INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, RECORD_AUDIO, VIBRATE, foreground-service/data-sync/special-use permissions, and READ/WRITE_EXTERNAL_STORAGE capped at API 28. It declares android:allowBackup=true and exported TestStorageActivity, ImportControlActivity and scoped FolderProvider components. A narrow account-directory backup exclusion is proposed in PR #61 but is not yet merged/verified against the final APK. Review the merged manifest, entry-point intent handling, and provider URI/permission boundaries before release. | Manifest source verified; installed package/security acceptance still required |
| Java/game runtime | Runtime selection, Java 8/17/21 components, native graphics/input and renderers are inherited from Amethyst and customized rather than reimplemented here. Exact compatibility must be tested per device, ABI, renderer and Minecraft release. | Partially verified |
| Tests | Python unittest regression suites and shell syntax checks run in GitHub Actions, followed by a Gradle build, APK signature/package/minimum-SDK checks, and Android emulator install/launcher foreground smoke test. | Workflow-defined; latest pass details are recorded in README and its linked Actions run |
| Physical gameplay | A successful emulator launcher startup does not prove Microsoft sign-in, complete downloads, opening a world, shaders, gamepad integration, or physical-phone performance. | Acceptance testing required |

## Feature status matrix

| Area | Status | Source-evidenced functionality and gaps |
|---|---|---|
| Branding, app icon and home UI | Partially implemented | VoidCraft branding, night-violet theme, responsive home customizations, account shortcut and quick folder actions exist in the patch scripts. Manual tap/visual/accessibility acceptance is not established by the startup smoke test. A complete dashboard with all requested cards/statuses is not verified. |
| Microsoft and local accounts | Partially implemented; security remediation in progress | Upstream Microsoft/local profile flow remains inherited/customized. At the pinned source, MinecraftAccount writes accessToken, clientToken and msaRefreshToken fields to account JSON under the app-private data root/accounts directory; this is plaintext storage, not Keystore-backed encryption. MicrosoftBackgroundLogin also logs the OAuth authorization code/form body, access/Xbox/XSTS/Minecraft tokens and raw Minecraft profile/name/UUID in the pinned source. PR #61 proposes excluding account JSON from Android backup/transfer and its CI build/package/API 28 launcher smoke test passed on run [#39116546902](https://github.com/hsudais201-ops/myLauncher/actions/runs/39116546902); it remains unmerged. PR #62 proposes removal of credential-bearing auth logs; its regression stage has passed, while the full build/emulator workflow was still running at the last audit update. Local profiles are not authenticated accounts. Keystore-backed token storage/migration, full OAuth/Xbox/entitlement/refresh acceptance and physical-device sign-in still need verification. |
| Version metadata and repair | Partially implemented | Upstream version lists/downloads are present; VoidCraft adds a Verify/repair version workflow and guards for missing/unreadable/empty manifests and inherited versions. Actual damaged installation repair on a device and custom-version handling are not fully verified. |
| Download manager | Partially implemented | Upstream async downloader, integrity checking and mirrors exist. A standalone central queue with priorities, persistent recovery, bandwidth limits and user control semantics is not verified. |
| Instance isolation/management | Partially implemented | Upstream profile/game-directory facilities exist, and folder shortcuts use the selected profile. Full first-class instance create/clone/import/export/archive/tag/search/compare workflows and isolation tests need verification. |
| Java/runtime selection | Partially implemented | Upstream runtime management and Java 8/17/21-related components exist. ABI/native library compatibility, trusted runtime installation/repair, per-instance constraints and device matrix need verification. |
| Launch/process management | Partially implemented | The upstream launch pipeline exists. CI checks the launcher activity is foregrounded; this is not proof that Minecraft launches or returns safely from an actual world session. |
| Graphics and performance | Partially implemented | Low-memory heap defaults and a resolution-scaling default are customized; renderer options are inherited; one version-gated MobileGlues workaround exists. User-visible settings, supported renderer matrix, thermal behavior and FPS claims need evidence. |
| Touch, keyboard, mouse and gamepad | Partially implemented | Upstream custom controls and input helpers exist. A complete drag/drop editor, export/import, layout presets, per-instance layouts and hardware-device acceptance are not confirmed. |
| Loader installation | Partially implemented | Upstream loader components include Forge, NeoForge, Fabric-like installers and OptiFine-related flows. Compatibility, repair/removal, Quilt/legacy coverage and Android-specific acceptance require verification. |
| Mod management | Partially implemented | Mods folder access and a guarded long-press enable/disable toggle for top-level JAR files exist. Mod download/install/update, provider search, dependency resolution, conflict analysis, rollback and compatibility reports remain missing or unverified. |
| Modpack management | Partially implemented | Upstream Modrinth/CurseForge-related API/components exist. This does not prove complete provider search/download/install/import/export/update functionality on Android. Archive/provider-specific workflows need tests. |
| Resource packs and shaders | Partially implemented | Resource-pack/shader-pack folder access and guarded top-level ZIP enable/disable toggles exist. Full discovery, install/update, ordering, activation and renderer compatibility checking remain missing or unverified. |
| Worlds and backups | Partially implemented | World-save folder access, timestamped ZIP backup, staged restore, read-only verification with path/entry/size/CRC safeguards, and confirmed backup deletion are documented as merged. Device-level dialogs and restoration of varied world archives need acceptance. Cloud/scheduled backup and conflict-aware sync are missing. |
| File manager/storage | Partially implemented | Selected-profile folders can be opened and Android storage abstractions are inherited. A full integrated file browser, SAF import/export coverage, broad file operations, storage dashboard and cache cleanup are not confirmed. |
| Logs and crash diagnosis | Partially implemented | Bounded live-log filtering, clear/match counts, redacted latest-log copy/share, heuristic diagnosis, and bounded crash-report sharing are described in the README/roadmap. Diagnosis is heuristic, not guaranteed root-cause analysis. |
| Settings import/export/reset | Partially implemented | A versioned, allowlisted portable-preference JSON export/import/reset flow exists in the current audit trail. It intentionally excludes credentials, account identity, custom arguments, world data and other sensitive/nonportable items. Full launcher setting coverage is not implied. |
| Multiplayer/server management | Missing or not verified | A roadmap list exists, but no source-evidenced complete server list editor, status checker, profile-specific server manager or compatible sharing workflow was confirmed in this audit. |
| Notifications/background tasks | Missing or not verified | Android WorkManager/foreground-work suitability, notification permission behavior, download completion/failure notifications and process-restart recovery are not confirmed as a complete feature. |
| Localization/accessibility/responsive layouts | Partially implemented | Portrait/landscape UI customizations exist. Formal screen-reader labels, contrast/text scaling audits, reduced motion, localization parity, tablet and Chromebook acceptance are not confirmed. |
| Security/privacy | Partially implemented; high-priority remediation pending | Safe ZIP handling, path confinement in several workflows, and bounded/redacted user-selected log sharing exist. Separate from that export redaction, pinned upstream Logcat statements expose live auth credentials/profile identifiers; PR #62 adds guarded redaction tests and is awaiting full CI review. PR #61's Android backup exclusions passed CI but are not merged. Credentials are still saved in account JSON without verified at-rest encryption. Audit remaining manifest/exported components, provider URI boundaries, download sources, dependencies, permissions and update integrity before release. |
| App update/rollback | Missing or not verified | Stable/development channels, signed update verification, migration, automatic backup and rollback are not confirmed. |
| Cloud sync, plugins and advanced services | Missing; phase-later optional | Cloud sync, scheduled backup, community/plugin APIs, cross-device conflict resolution and optional analytics/telemetry are not verified and must not be implemented without consent and explicit security design. |
| Documentation | Partially implemented | README and FEATURE_ROADMAP.md exist. This change adds separate audit, architecture, implementation plan, testing and changelog docs; these describe the current architecture and remaining work, not unimplemented behavior as shipped. |

## Highest-priority tasks

1. **Close the authentication-data exposure.** Review PR #61 (backup exclusion; its CI build/package/API 28 smoke test passed) and PR #62 (Logcat secret redaction; final CI result was pending at the last status check). Keep both explicitly unmerged until CI/review is satisfactory. Next design Keystore-backed token encryption with backward-compatible migration, covering account load/save, refresh/login flows, imports, errors and recovery. Backup exclusion and log redaction are mitigations, not encryption at rest.
2. **Harden install/launch recovery.** Extend deterministic tests for incomplete downloads, retry/cancel semantics, storage exhaustion, metadata inheritance, corrupt archives and safe repairs without deleting worlds/configuration. The repeatable source audit in PR #60 passed workflow run [#39116190501](https://github.com/hsudais201-ops/myLauncher/actions/runs/39116190501), including tests, APK build/package checks and API 28 x86_64 launcher startup; PR #60 remains unmerged.
3. **Prove compatibility and user flows.** Expand emulator checks beyond foreground startup where feasible; add physical ARM64/device gameplay acceptance for Microsoft sign-in, version install, world launch, renderers, touch controls, storage and process recovery.

## Evidence links

- [Repository README](https://github.com/hsudais201-ops/myLauncher/blob/main/README.md)
- [Current feature roadmap](https://github.com/hsudais201-ops/myLauncher/blob/main/FEATURE_ROADMAP.md)
- [Build workflow](https://github.com/hsudais201-ops/myLauncher/blob/main/.github/workflows/build.yml)
- [Customization patcher](https://github.com/hsudais201-ops/myLauncher/blob/main/.github/scripts/customize_amethyst.py)
- [Regression tests](https://github.com/hsudais201-ops/myLauncher/blob/main/.github/scripts/test_customizer.py)
- [Upstream engine](https://github.com/AngelAuraMC/Amethyst-Android)

### Audit limitations

This audit was based on repository documentation, key workflow files, and portions of the deterministic patcher and regression suite. It did not fetch and compile the upstream engine in this session, inspect every upstream source file, run CI, run an emulator, or launch Minecraft. Any claim requiring those steps remains marked as partial or unverified.
