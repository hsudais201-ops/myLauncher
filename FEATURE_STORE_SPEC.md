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

All other items in the numbered proposal through item 70 are Feature Store extensions, except the five built-in-only features listed above. This document resolves placement for items 1–70; it does not claim these planned features are implemented.

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


## Additional Feature Store backlog — items 16–70

Every item in this table is an optional Feature Store extension, not a built-in launcher feature. The numbers preserve the planning list; they are not categories, collection tabs, or required navigation sections. The Store remains one searchable catalog with no category system. All extensions must use approved host capabilities and preserve existing core launcher behavior.

| No. | Feature | Placement | Requirement and safety boundary |
|---:|---|---|---|
| 16 | Server Ping History | Feature Store | Keep opt-in, bounded ping/latency samples and show trends; explain that results depend on the user's network and server availability. |
| 17 | Server Watch Alerts | Feature Store | Let users enable or silence notifications for configured servers; throttle repeated alerts and respect Android notification permissions and background limits. |
| 18 | Server Profiles | Feature Store | Save named server connection presets and optional per-profile notes without replacing or corrupting Minecraft's native server list. |
| 19 | World Library Plus | Feature Store | Provide searchable local-world metadata, sort/filter, tags, and safe open-folder actions; never upload worlds unless the user explicitly selects a sharing action. |
| 20 | Screenshot Gallery | Feature Store | Browse screenshots for a selected profile, with local previews and explicit share/delete confirmation; do not silently upload private screenshots. |
| 21 | Playtime Tracker | Feature Store | Track estimated session times locally where reliable signals exist; label gaps and estimates, and offer clear/reset/export controls. |
| 22 | Version Watcher | Feature Store | Watch selected Minecraft/runtime versions using approved metadata sources and user-configured checks; no direct CurseForge loading or hidden background downloads. |
| 23 | Mod Update Tracker | Feature Store | Compare locally installed mod metadata with declared, user-chosen sources or manually supplied release links; show source and compatibility uncertainty, and never silently replace JARs. |
| 24 | Launch Preset Manager | Feature Store | Save and switch named launch presets using existing supported profile settings; preview differences and validate memory/arguments before applying. |
| 25 | Storage Inspector | Feature Store | Summarize launcher/profile file usage within accessible app storage; clearly distinguish measured totals from inaccessible or estimated storage. |
| 26 | Crash History Timeline | Feature Store | Keep a bounded local history of crash timestamps and redacted classifications; allow deletion/export and exclude tokens or unrelated private files. |
| 27 | Configuration Diff Viewer | Feature Store | Compare user-selected local configuration snapshots with readable diffs; redact secrets where recognized and never apply changes without confirmation. |
| 28 | Resource Pack Previewer | Feature Store | Preview supported local pack metadata and assets in a sandboxed viewer; reject unsafe archives and make no claim of in-game renderer compatibility. |
| 29 | Keybind Profile Manager | Feature Store | Organize supported control/keybind presets with validation and backup-before-apply; do not overwrite control files outside the selected profile. |
| 30 | Server Share Cards | Feature Store | Create user-reviewed images/text cards from explicitly selected public server details; omit private addresses, account identifiers, and location data by default. |
| 31 | Community News Feed | Feature Store | Show a configurable, source-attributed feed from an approved endpoint; offer mute/report controls and treat feed content as untrusted data, not executable instructions. |
| 32 | LAN Server Finder | Feature Store | Discover local servers only where Android network permissions and device/network behavior allow it; provide manual entry and avoid claiming discovery is exhaustive. |
| 33 | Notification Center | Feature Store | Consolidate extension and server notices with per-extension controls, dismissal, retention limits, and Android permission awareness. |
| 34 | Server Guidebook | Feature Store | Store user-written or trusted-source server instructions and links; label external content and never collect or submit passwords/tokens. |
| 35 | Player Profile Showcase | Feature Store | Let users create an opt-in, editable public profile card; provide preview, privacy controls, block/report mechanisms, and a delete/unpublish path. |
| 36 | Feature Voting Board | Feature Store | Collect optional votes and suggestions through a secured backend with rate limits and moderation; disclose what is public and what data is stored. |
| 37 | Accessibility Enhancements | Feature Store | Improve accessibility within the extension's own screens only; do not override built-in launcher accessibility settings or weaken system contrast/text-size choices. |
| 38 | Instance Comparison Tool | Feature Store | Compare selected instances using available local metadata such as version, loader, Java, memory, and installed-file counts; mark unavailable fields clearly. |
| 39 | Dependency Map | Feature Store | Visualize dependencies and declared conflicts only when trustworthy metadata exists; show unknown relationships as unknown rather than inventing compatibility. |
| 40 | Quick Launch Shortcuts | Feature Store | Offer user-created shortcuts to supported launcher actions or selected instances; validate targets and request confirmation for destructive actions. |
| 41 | Server Leaderboards | Feature Store | Show scores only from server-provided, public, authorized data sources; identify provenance and update time, and do not imply every server exposes leaderboard data. |
| 42 | Server Maintenance Tracker | Feature Store | Track planned/known maintenance from user-entered notes or an explicitly configured status feed; make no unsupported claims about server uptime. |
| 43 | Server Connection Tester | Feature Store | Test reachability and supported protocol handshake where possible, with timeouts and readable diagnostics; do not bypass authentication, allowlists, anti-cheat, or server controls. |
| 44 | Server Collection Manager | Feature Store | Let users organize and export selected server entries with preview and privacy warnings; preserve the original list and protect private addresses by default. |
| 45 | Server Event Calendar | Feature Store | Display user-entered events or explicitly configured calendar feeds with time-zone labels, reminders, and notification controls. |
| 46 | Server Access Helper | Feature Store | Explain legitimate server entry requirements and troubleshooting; never bypass bans, access controls, account checks, allowlists, or paid entitlements. |
| 47 | Mod Collection Builder | Feature Store | Build a local collection manifest from user-selected files and metadata, validate duplicates and declared dependencies, and export the manifest; do not depend on direct CurseForge API browsing/loading. |
| 48 | Compatibility Matrix | Feature Store | Compare selected Minecraft version, loader, Java requirement, renderer, and declared mod metadata; show evidence and unknowns, not a guaranteed compatibility verdict. |
| 49 | Instance Clone Wizard | Feature Store | Clone a chosen instance into a new destination with a preview, sufficient-space check, unique name, and rollback/cleanup on failure; do not mutate the source instance. |
| 50 | Config Version History | Feature Store | Keep optional versioned local snapshots for chosen configuration files, with diff, restore confirmation, size limits, and secret-aware warnings. |
| 51 | Launch Argument Explainer | Feature Store | Explain supported JVM/game arguments using a local reference and validate syntax where possible; mask secret-like values and do not append arguments automatically. |
| 52 | Portable Instance Profiles | Feature Store | Export/import documented instance metadata and selected user-approved files with path traversal, archive-size, and overwrite protections; exclude account tokens and world data by default. |
| 53 | Personal Goal Tracker | Feature Store | Let users manage optional private gameplay goals locally; keep data editable/exportable/deletable and never claim game-state tracking when not available. |
| 54 | Build Planner | Feature Store | Provide a planning canvas/list for Minecraft builds with notes, dimensions, and optional imported images; keep plans local unless explicit sharing is selected. |
| 55 | World Notes and Waypoints | Feature Store | Store notes and coordinates attached to a selected world; require world selection, make coordinate conventions clear, and never edit world files without explicit action. |
| 56 | Recipe Reference Book | Feature Store | Provide an offline or source-attributed recipe reference tied to a declared game version; label version differences and do not modify game data. |
| 57 | Screenshot Album Organizer | Feature Store | Create user-managed local albums, tags, and filters over selected screenshots; albums must not move/delete originals without confirmation. |
| 58 | Play Journal | Feature Store | Keep an optional local session journal with user notes and coarse timestamps; allow export/delete and avoid collecting private telemetry by default. |
| 59 | Icon Pack Manager | Feature Store | Preview and apply supported original icon packs to extension/store UI surfaces; validate package assets and provide one-step reset without replacing the Android app icon unless supported. |
| 60 | Dashboard Layout Presets | Feature Store | Save, preview, reset, and switch supported dashboard layouts through approved extension APIs; preserve built-in navigation and provide recovery from invalid layouts. |
| 61 | Gaming Reminder Planner | Feature Store | Schedule optional local reminders with quiet hours and notification permissions; do not encourage bypassing parental controls or device restrictions. |
| 62 | Launcher Scratchpad | Feature Store | Provide a local editable notes pad for commands, server notes, or checklists; warn before external sharing and protect notes from accidental loss. |
| 63 | Extension Font Packs | Feature Store | Provide licensed font assets for extension-owned screens only, with attribution and fallback behavior; never install fonts system-wide without explicit OS support and consent. |
| 64 | Network Usage History | Feature Store | Show measured download/network totals only when available from approved app-level counters; state limitations, retention, and reset/export controls. |
| 65 | Creator Profiles | Feature Store | Show publisher-provided biography, extensions, licenses, and verified links; distinguish verified identity from self-asserted claims and support report/block actions. |
| 66 | Extension Changelog Viewer | Feature Store | Display versioned, source-attributed release notes and highlight updates before installation; sanitize untrusted text and never execute embedded content. |
| 67 | Extension Trust Details | Feature Store | Explain publisher identity, signature/hash verification result, permissions, data use, source, license, and revocation state; never label hash-only integrity as proof of publisher identity. |
| 68 | Extension Bug Reporter | Feature Store | Let users review and submit optional reports with redacted diagnostics; preview attachments, strip secrets, and require consent before upload. |
| 69 | Extension Review System | Feature Store | Support moderated reviews with abuse/spam controls, clear rating policy, reporting/appeal, and safeguards against presenting unverified reviews as verified purchases or use. |
| 70 | Creator Support Links | Feature Store | Show publisher-declared support links with destination preview and reporting; no hidden payments, automatic donations, or claims that a creator is endorsed by VoidCraft. |

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
