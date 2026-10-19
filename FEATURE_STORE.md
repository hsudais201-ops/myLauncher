# VoidCraft Feature Store

## Status and scope

The Feature Store already exists in the launcher, so this change adds a proposed catalog for that existing store; it does not propose building a second store. The catalog is a planning/data artifact only: entries are marked `proposed` until their actual in-app pages, install/update/uninstall behavior, permissions, and tests exist.

The catalog is intentionally flat and has no categories. Every installed add-on should have a dedicated page and appear in the installed-features list.

## Deduplication rules

The current `FEATURE_ROADMAP.md` and `README.md` were checked for overlaps. The launcher already has or inherits profile/runtime/account/download functionality, low-memory defaults, theme customization, Mods/Resource packs/Shader packs folder actions, manual world ZIP backup/restore, bounded crash-log diagnosis and sharing, and Screenshots folder access. Add-ons must extend these existing capabilities instead of duplicating them.

Keep these in the launcher core, not as store add-ons: Java runtime and native libraries; engine, renderer integration and essential launch pipeline; Minecraft version/library/asset downloads; account/profile infrastructure; modpack import/export; and the touch-control designer. Do not remove or replace these systems to implement the store.

## Proposed store catalog

1. Smart Performance Advisor — explain device-aware performance recommendations; never silently overwrite saved settings.
2. Server Status Monitor — add/edit/remove/refresh servers; show online state, MOTD, icon, player count and latency.
3. Custom Theme Studio — preview, apply, switch and uninstall themes while preserving the built-in VoidCraft theme.
4. Live Performance HUD — show only metrics genuinely exposed by the device/runtime.
5. Scheduled World Backup Automation — schedules and retention built on the existing safe ZIP backup/restore implementation.
6. Advanced Instance Toolkit — advanced clone, icon, tag, favorite, search and sorting workflows only where the core lacks them.
7. VoidCraft Social — optional username profile, friend requests/removal, blocking/reporting and private chat; requires a separate secure backend and consent.
8. Community World Gallery — reviewed worlds with creator attribution, license and compatibility information.
9. Resource Pack Previewer — metadata/screenshot preview and version details, reusing existing pack storage and toggles.
10. Download Queue Enhancer — unified view over existing supported download tasks, without replacing the downloader or faking unsupported pause/resume.
11. Shader Compatibility Checker — evidence-based version/renderer checks, with uncertainty clearly stated.
12. Mod Compatibility Checker — inspect dependencies, versions and likely conflicts; never automatically delete or disable files.
13. Quick Settings Profiles — previewed safe preference presets using an allowlist.
14. Enhanced Crash Log Analyzer — extend current heuristic diagnosis; never upload logs without explicit review and consent.
15. Playtime Tracker — local session and per-instance statistics by default.
16. Screenshot Gallery Plus — thumbnails, sorting and favorites extending the existing Screenshots folder access.
17. Modpack Update Watcher — updates from signed VoidCraft manifests or trusted creator feeds; do not use Modrinth/CurseForge APIs for the Feature Store.
18. Community Feature Showcase — part of the existing store shell, not a separately installable add-on; supports search, creator/review information, compatibility and install states.
19. Accessibility Enhancements — extra contrast, text scaling, reduced motion and larger tap targets where not already available.
20. Storage Cleaner — review disposable temporary files only; never target worlds, backups, profiles, accounts, mods, runtimes, native libraries or game assets.

## Install, update and uninstall requirements

- Show creator, version, changelog, app/OS/ABI compatibility, permissions, data locations, package signature/hash and review status before installation.
- Verify package signature and SHA-256, validate manifest schema and file paths, reject path traversal and undeclared permissions, and stage installation before activation.
- Use an explicit allowlist of launcher APIs. Do not execute arbitrary downloaded bytecode, native libraries, shell scripts or APKs as feature packages.
- Require user consent for sensitive permissions. Never silently upload logs, messages, account information or files.
- Update transactionally with rollback. Uninstall only feature-owned files and preserve user-created data by default.
- Community uploads remain quarantined until owner/support review, permission review, compatibility tests and security checks pass. A future web testing portal must feed the same quarantine pipeline.
- Test install, denied permissions, invalid signature, malformed archive, path traversal, duplicate IDs, update, failed activation, rollback, uninstall/reinstall, low storage and preservation of existing worlds/settings.

## Acceptance status

All catalog entries are proposals, not claims of implementation. Before marking a feature available, implement and test its actual dedicated page and install lifecycle in the existing store, run regression tests, build/package checks, emulator launch checks, and physical-device checks where required. Preserve current architecture and user data.
