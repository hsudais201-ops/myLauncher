# VoidCraft Launcher Feature Store and Built-in Feature Placement

**Status:** Product requirement / implementation specification  
**Updated:** 2026-10-19

## Placement rule

The Feature Store is the place to discover, install, open, update, and uninstall optional extension features. The five features named below stay built into VoidCraft Launcher and must not be offered as installable Store extensions:

- **Crash Assistant** (item 4 from the feature proposal)
- **Touch Control Designer** (item 7)
- **Device Compatibility Checker** (item 8)
- **Modpack Import & Export** (item 9)
- **Performance Advisor** (item 11)

All other features from that same 15-item proposal are Feature Store extensions. This document resolves placement; it does not claim these planned features are implemented.

## Feature placement matrix

| No. | Feature | Placement | Requirement |
|---:|---|---|---|
| 1 | Server Status Monitor | Feature Store | Add, edit, remove, refresh, and favorite server entries. Show online/offline, MOTD, server icon when supplied, player count, latency, and version where a status protocol can provide it. |
| 2 | VoidCraft Friends & Chat | Feature Store | Separate VoidCraft social identity; username/profile, friend requests, remove/block/report, online status, and private chat. It must not impersonate Microsoft/Xbox authentication or replace Minecraft account sign-in. Requires a real secured backend before it can be called functional. |
| 3 | Mod Manager Plus | Feature Store | Advanced management on top of existing core mod-folder behavior: metadata, duplicate/conflict hints, compatible enable/disable actions, and clear recovery. Never promise compatibility without evidence. |
| 4 | Crash Assistant | **Built into launcher** | Accessible from logs/crash diagnostics. Read logs safely, redact secrets, explain likely causes and remediation, and label diagnoses as best-effort. No Store install required. |
| 5 | Theme Studio | Feature Store | Browse, preview, install, enable, update, and uninstall optional original themes and visual packs. |
| 6 | World Backup Manager | Feature Store | Enhanced backup UI and workflows. Preserve existing core world backup/restore capabilities; the extension must never replace or silently delete user worlds/backups. |
| 7 | Touch Control Designer | **Built into launcher** | Accessible from launcher settings/control configuration, not as a Store extension. Support editing positions/sizes/transparency, multiple layouts, left-handed and game-mode presets, reset, and safe import/export where supported by the existing controls system. |
| 8 | Device Compatibility Checker | **Built into launcher** | Available before launch and from diagnostics. Report architecture, Android/device limits, Java/runtime fit, renderer capabilities and known version limitations; distinguish detected facts from estimates. |
| 9 | Modpack Import & Export | **Built into launcher** | Provide file-picker-based import/export for explicitly supported local archive/manifest formats. Validate paths, archive sizes, hashes when available, dependencies and conflicts; preview changes; never overwrite profiles/worlds silently. No direct CurseForge API loader is required for this workflow. |
| 10 | Resource Pack Manager | Feature Store | Enhanced browsing, preview, ordering, safe enable/disable and compatibility warnings for local packs. Keep currently working core folder access. |
| 11 | Performance Advisor | **Built into launcher** | Use device/runtime evidence to recommend safe memory and graphics settings. Preview and confirm changes; do not promise FPS improvements or exceed device-safe limits. |
| 12 | Download Center Plus | Feature Store | Richer UI for the existing downloader: queue, progress, retry/cancel where the downloader supports it, integrity status, and readable error detail. Extend rather than replace the core downloader. |
| 13 | Shader Compatibility Assistant | Feature Store | Inspect pack metadata and renderer requirements where available; warn about known incompatibilities without promising that a shader will run. |
| 14 | Dashboard Widget Maker | Feature Store | Optional dashboard widgets with per-widget settings, layout controls, and disable/uninstall. Widgets may only use approved, declared extension capabilities. |
| 15 | Community Feature Creator | Feature Store / publishing workflow | Allow extension submissions with metadata, screenshots, version history, license declaration, permission declarations, moderation, reports, and revocation. Uploading does not make an extension automatically trusted or immediately public. |

## Store experience — no category system

- Present one searchable catalog. Do not introduce mandatory feature categories, category tabs, or category-based navigation.
- Search, sorting (for example featured, recently updated, or name), and direct links to a feature are allowed; these are not a category system.
- A listing should show the feature name, author/publisher, description, screenshots, version, supported launcher/API versions, requested permissions, changelog, license, and install/update state.
- After installation, the feature must appear in an **Installed Features** list and have its own page/screen and settings. Users can open, update, disable, and uninstall it without searching the catalog again.
- Built-in items 4, 7, 8, 9, and 11 belong in the launcher UI and should not show as Store items with an Install button.
- The Store must distinguish official VoidCraft-published items from community submissions, and must show unavailable/incompatible/disabled states rather than pretending an install succeeded.

## Extension manifest and storage model

Each feature package should be described by a validated, versioned manifest with at least:

- stable extension ID and display name;
- publisher identity and signed-publisher status;
- extension version and minimum/maximum supported Feature Store API version;
- entry screen and declared capabilities;
- permissions, data-storage needs, network endpoints (if any), license, checksums/signature and changelog;
- required dependencies and compatibility constraints.

Keep each extension's files and preferences isolated, for example under app-private storage using a stable extension ID and versioned data directory. Keep the installed-state registry separate from package files so a failed update can be rolled back. Uninstalling an extension removes its code/assets after confirmation but should ask whether to preserve or delete its user data. Do not store Microsoft/Xbox access or refresh tokens in extension-readable storage.

## Security and trust requirements

- Never execute arbitrary uploaded code with unrestricted app, filesystem, account-token, native-library, or shell access.
- Prefer declarative UI and a limited host capability interface. Every permission must be scoped, visible before install, and denied by default.
- Verify publisher signature and package hash before activation; use an allowlisted HTTPS source policy and signed repository metadata. A hash alone proves integrity against accidental corruption, not publisher identity.
- Validate archives and manifests against traversal paths, duplicate entries, decompression bombs, oversized files, invalid IDs, unsupported API versions, and dependency cycles.
- Install/update atomically; retain a rollback point until activation succeeds. A failing extension must be disableable without blocking launcher startup.
- Community uploads require review, license/ownership attestations, abuse reporting, version revocation, and a security response path.
- Any extension requiring network access (such as social chat) must declare it and use an authenticated, rate-limited service. Do not claim account registration, friend search, presence, or chat works until its backend and privacy/data-retention policy exist.

## Relationship to core launcher components

This redesign is additive. Preserve the existing Amethyst-based engine/runtime, Java runtimes, native libraries, renderer/input components, Minecraft version metadata/download system, loader installers, account flow, and user data unless a separate source audit proves a specific change is safe and is explicitly approved. Store extensions may call supported host capabilities; they must not bundle duplicate launchers or replace core subsystems.

Do not build the Feature Store around direct Modrinth/CurseForge API browsing or direct CurseForge file loading. The built-in Modpack Import & Export workflow is local-file-first and supports only explicitly documented formats. Do not remove or rewrite existing upstream integration code as a side effect of this design without a separate dependency/licensing and regression audit.

## Implementation order and acceptance gates

1. **Foundation:** define the manifest schema, Feature Store API, signed catalog format, isolated storage, install states, permission UI, uninstall-data policy, and rollback behavior.
2. **Catalog UI:** searchable single catalog, feature detail page, Installed Features list, per-extension screen contract, loading/error/empty/offline states.
3. **First safe extensions:** Theme Studio and Server Status Monitor, after host capabilities, status parsing, and failure handling are tested.
4. **Remaining Store features:** Mod Manager Plus, World Backup Manager extension, Resource Pack Manager, Download Center Plus, Shader Compatibility Assistant, Dashboard Widget Maker, social chat backend, and community publishing workflow.
5. **Built-in launcher screens:** integrate Crash Assistant, Touch Control Designer, Device Compatibility Checker, Modpack Import & Export, and Performance Advisor outside the Store.
6. **Release gate:** regression tests for existing launch/download/login/worlds, Android 9/API 28 emulator build-and-launch, supported-device acceptance, migration/backward-compatibility tests, store security tests, and clear documentation of any feature not yet implemented.

A feature is complete only after its real behavior, error paths, security boundaries, tests, and supported-device status are verified. A listing, mockup, or placeholder screen alone does not count as implementation.
