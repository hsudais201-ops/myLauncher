# VoidCraft Launcher Implementation Plan

**Baseline:** 2026-10-18  
**Specification:** [MASTER_MOBILE_LAUNCHER_SPEC.md](MASTER_MOBILE_LAUNCHER_SPEC.md)  
**Audit:** [FEATURE_AUDIT.md](FEATURE_AUDIT.md)

This plan deliberately starts from the current customization-over-upstream architecture. It avoids duplicating upstream features until source behavior and Android acceptance have been verified.

## Operating rules

- Work from a focused branch and open a pull request; do not make unrelated rewrites.
- Before code changes, identify exact upstream source paths and patcher/test ownership.
- Keep patch scripts deterministic and idempotent, and make unexpected upstream changes fail closed.
- Write regression coverage for each source customization, including safe failure paths.
- Do not upgrade audit statuses based on source presence alone.
- Preserve worlds, settings and account data; never silently replace conflicting user content.
- Do not claim device/gameplay compatibility from APK assembly or launcher foreground status alone.
- Run relevant tests; record blocked tests and hardware gaps honestly.

## Priority order

### P0 — Baseline audit and release-critical safety

**Goal:** know exactly what the pinned engine supports before expanding scope.

Tasks:
- Inspect manifest, Gradle app/module configuration, target and minimum SDK, permissions, product flavors, ABI filters, native libraries and bundled runtimes.
- Trace launcher navigation, activity lifecycle, selected-profile storage, instance/game-directory logic, version metadata, async downloads, runtime selection, launch arguments, process monitoring and renderer/input handoff.
- Inspect Microsoft OAuth/Xbox/entitlement/session flow, token persistence and redaction boundaries.
- Verify archive processing and all file delete/rename/restore operations against Android storage APIs.
- Run the existing Python regression suite and shell/Python syntax validation, then run the full GitHub Actions build workflow after the source baseline is captured.
- Update FEATURE_AUDIT.md with actual source paths, CI run identifiers, unsupported hardware, and evidence for each status.

Acceptance: exact SDK/ABI/runtime facts are documented; existing regression tests have recorded results; no existing functionality is rewritten before understanding it.

### P1 — Install, download and launch resilience

**Goal:** reliably get from a compatible selected version to an actual game session.

Tasks:
- Add tests for missing, empty, malformed and inherited version metadata; partial/corrupt file detection; hash mismatch; transient and permanent download failures; retry/cancel cleanup; duplicate downloads; and low disk space.
- Verify that partial files never become an installed version until integrity checks complete.
- Make task states honest and recoverable following cancellation, activity recreation and process death where the current downloader architecture allows it.
- Verify runtime/Java/Minecraft compatibility checks and safe memory allocation before launch.
- Capture clear, redacted launch failure diagnostics; avoid freezing launcher UI and prevent duplicate process startup.
- Preserve the existing Verify / repair version behavior and ensure repairs do not remove worlds, configs, backups or unrelated profiles.

Acceptance: regression tests prove safe behavior for each introduced path; CI APK/package checks pass; emulator smoke tests pass; real game-launch acceptance is separately recorded.

### P2 — Core mobile launcher and instance experience

**Goal:** surface existing capabilities consistently without inventing unsupported controls.

Tasks:
- Audit home/portrait/landscape navigation and every visible button.
- Add real loading/empty/error/retry/permission-denied states.
- Verify selected-instance context for mods, resource packs, worlds, logs, screenshots, crash reports and configuration.
- Define a stable instance metadata model only if upstream profile facilities cannot meet the requirement.
- Add isolated create/clone/rename/delete/import/export/backup workflows where missing, with collision checks and tests.
- Improve dashboard status for selected version, account, downloads, storage and compatibility using real state sources.

Acceptance: automated navigation/state tests and device taps confirm the advertised action works; instance operations keep user data isolated.

### P3 — Authentication and data protection

**Goal:** complete legitimate authentication and storage protection without weakening license gates.

Tasks:
- Trace OAuth, Xbox authentication, Minecraft entitlement/profile checks and refresh/expiry/logout lifecycle.
- Verify secure storage strategy and ensure tokens never enter clipboard exports, logs, crash shares or settings JSON.
- Test network failure, session expiry, account switching and sign-out.
- Distinguish local profile labels from authenticated accounts in UI/help text.
- Review all app permissions and file sharing paths against Android version/store constraints.

Acceptance: authentication is tested with permitted credentials on a supported environment; no password collection, fake identity or entitlement bypass; diagnostic exports are redacted.

### P4 — Controls, graphics and performance

**Goal:** implement only runtime-supported input and rendering options.

Tasks:
- Audit current custom-control editor, saved layout format, keyboard/mouse/gamepad maps, touch/game lifecycle and safe areas.
- Add layout editing/presets/reset/import/export only after validating data compatibility.
- Verify ABI and native-library support, renderers and graphics translation layers per Minecraft version.
- Make RAM/resolution presets device-aware and range-checked. Keep user choices intact when a workaround is applied.
- Test low-memory, low-storage and activity-interruption behavior; record physical hardware results.

Acceptance: settings alter the actual game behavior; unsupported options are hidden/disabled with explanations; there are no unsupported FPS claims.

### P5 — Loaders, mods and provider integrations

**Goal:** secure, compatible modding workflows on Android.

Tasks:
- Map existing loader installers and supported Minecraft/loader combinations.
- Add provider metadata/version filters and legitimate download APIs where legally permitted.
- Validate mod filenames, manifests, dependencies, side/loader compatibility and checksum metadata.
- Implement install/remove/enable/disable/update/import/export with pre-update backup and rollback.
- Add known-conflict/missing-dependency diagnostics, with clear distinction between authoritative metadata and heuristics.
- Apply zip-slip, path traversal, archive-bomb, duplicate-name and disk-space protection to all imports.

Acceptance: end-to-end install and failure-recovery tests pass for named supported examples; restricted files are handled under provider terms; desktop-only mods are clearly flagged.

### P6 — Modpacks, resource packs, worlds and file tools

**Goal:** make content management safe and recoverable.

Tasks:
- Import supported .mrpack and CurseForge exports through explicit, validated parsers.
- Resolve pack file manifests through official/permitted providers; report missing or restricted files.
- Stage modpack updates and preview changes; preserve user-added content when possible.
- Finish resource/shader pack install and activation flows only for compatible runtimes.
- Extend world/file management with safe SAF import/export, backup selection, archive validation, no-overwrite restore and disk-space-aware staging.
- Keep removable cache cleanup separate from installed content and user backups.

Acceptance: crafted unsafe ZIPs are rejected; interrupted installs leave no corrupted final state; existing worlds/configurations are not silently overwritten.

### P7 — Downloads, notifications and mobile lifecycle

**Goal:** long-running work behaves responsibly on Android.

Tasks:
- Consolidate install/download task state when current code permits.
- Implement queue/cancel/retry/priority/concurrency controls and resume only for protocols that support it.
- Persist enough state to recover task summaries after process restart; clean stale temporary files safely.
- Use Android-supported scheduled/foreground work and permission models; avoid permanent unnecessary services.
- Add configurable notifications for completion/failure and handle denied notification permission.

Acceptance: network disconnection, app backgrounding, process death, cancellation and low storage produce predictable task states and safe recovery.

### P8 — Advanced services (optional)

Do not start until core launch and recovery paths are reliable.

Possible work:
- Server list/status/Quick Connect and import/export.
- Enhanced crash diagnosis and compatibility database.
- Playtime/session analytics stored locally by default.
- Cloud backups/settings sync with consent, encryption where appropriate, quotas and conflict resolution.
- Instance/pack/control-layout sharing, themes, plugin APIs and Chromebook optimizations.

Acceptance: each feature gets a separate threat model, platform/provider terms review, privacy defaults, compatibility tests and rollback strategy. Optional analytics or crash reporting requires explicit consent.

### P9 — Release readiness

Tasks:
- Expand automated tests for version metadata, runtime selection, arguments, settings, downloads, ZIPs, backups, modpacks, profile isolation and secret redaction.
- Add lint/static-analysis checks supported by the inherited build.
- Test current documented minimum Android and every claimed ABI on appropriate emulators/devices.
- Conduct a physical-device test matrix for touch, input, world launch, renderer, low RAM, thermal pressure and process recovery.
- Review licenses, notices, runtime distribution terms, mod/provider terms, permissions, exported components and update verification.
- Validate final APK package ID, minimum/target SDK, signing, SHA-256, native libraries and release notes.
- Document all tests that were unavailable and downgrade any unsupported compatibility claim.

## Immediate next task

**Priority 0 — credential protection.** The pinned Microsoft authentication source currently logs authorization codes/request bodies, OAuth/Xbox/XSTS/Minecraft tokens and Minecraft profile identifiers, while account JSON contains access/refresh-token fields without confirmed at-rest encryption. PR #61 proposes excluding `accounts/` from legacy/cloud/device-transfer backups; its full CI build/package/API 28 emulator smoke test passed in [run #39116546902](https://github.com/hsudais201-ops/myLauncher/actions/runs/39116546902), but the PR remains open. PR #62 proposes guarded auth-log redaction; its regression stage passed but its full CI workflow was still in progress at the last check. Review both PRs and merge only after final CI/review. Then design and test Android Keystore-backed credential encryption with a backward-compatible migration covering sign-in, token refresh, account switching, logout, old JSON files, corruption/recovery and import/export. Backup exclusion and log redaction are interim mitigations, not encryption at rest.

The source SDK/manifest/ABI audit is now automated in PR #60 and its build/test/emulator workflow passed in [run #39116190501](https://github.com/hsudais201-ops/myLauncher/actions/runs/39116190501); that PR also remains open. Continue next with download/install/launch recovery tests, then real-device gameplay acceptance for supported ABIs/renderers/input features. The emulator startup test is not gameplay certification.

## Definition of a finished ticket

Every ticket should include: observable behavior, affected upstream/source paths, data migration or backward compatibility notes, security/platform considerations, regression tests, actual test commands/results, device acceptance needed, documentation updates, and explicit known limitations.
