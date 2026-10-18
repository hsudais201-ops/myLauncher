# Master Mobile Minecraft Launcher Specification

**Project:** VoidCraft Launcher  
**Repository:** hsudais201-ops/myLauncher  
**Primary platform:** Android  
**Runtime foundation:** Pinned Amethyst-Android engine, customized by this repository  
**Document status:** Complete requirements baseline; not a claim that every item is implemented.

## Mission and non-negotiable rules

Develop the existing VoidCraft Launcher into a polished, secure, mobile-first launcher for Minecraft Java Edition on Android devices where the underlying runtime, graphics path, architecture, and game version are compatible. Preserve the current project and upstream engine architecture unless a measured technical reason supports a change. Do not replace the application with a new project merely to simplify development.

Use comparable launchers as feature references: PojavLauncher, MojoLauncher, Fold Craft Launcher, HMCL, Prism Launcher, MultiMC, Modrinth App, GDLauncher, the Official Minecraft Launcher, Lunar Client, and other compatible clients. Reuse ideas and interoperability—not proprietary implementation, branding, or access-control bypasses. Desktop capabilities must be reevaluated for Android rather than assumed portable.

A visible screen, menu item, or button is not proof of implementation. A feature is complete only when its behavior is implemented, inputs and failures are handled, user data is preserved, platform limits are respected, relevant tests exist and have actually run, documentation is accurate, and unresolved limitations are stated.

## Platform and mobile-first requirements

- Android-first interaction and navigation; support phones, tablets, and compatible Chromebooks where the underlying engine allows it.
- Responsive portrait and landscape layouts, display-cutout/safe-area handling, adaptive sizing, scrollable content, accessible touch targets, scalable text, and system display scaling.
- External keyboard, mouse, and gamepad support only where integrated into the actual game input pipeline.
- Correct activity lifecycle, foreground/background transitions, memory pressure handling, cancellation, and recovery after process termination.
- Android-compatible storage, work scheduling, and notification APIs. Do not assume unrestricted filesystem access, permanently running services, or arbitrary executable downloads.
- Minimize idle work, duplicate downloads, unnecessary dependencies, cache growth, and expensive visual effects on low-end devices.
- Check runtime, CPU architecture, graphics capability, free storage, available memory, network access, and permissions before costly operations.
- Explain incompatibilities plainly and provide supported alternatives rather than promising universal compatibility.

## Product requirements

### 1. Home dashboard

Provide launcher branding and app identity, a prominent Play action, selected instance/version/loader, account status, installation/launch status, recent instances and sessions, recent play history, supported featured modpacks or news, shortcuts to instances, mods, worlds, downloads, settings, and accounts, completion/error notifications, storage usage, and device/runtime compatibility status. Every action needs real navigation or behavior. Include loading, empty, success, disabled, error, and retry states. Keep the home view responsive on low-memory hardware.

### 2. Minecraft versions and installation

- Browse available stable releases; expose snapshots, prereleases, and older versions when metadata, runtime and licensing conditions permit.
- Show version ID and release information; select, install, remove, verify, and repair versions.
- Download client JARs, libraries, assets, manifests and required dependencies from legitimate metadata endpoints.
- Check downloaded content using trusted hashes or metadata when available; detect incomplete, missing, and corrupt files; avoid duplicate downloads.
- Queue, cancel, retry, pause/resume where supported, persist task state where feasible, and safely recover after an interruption.
- Show truthful progress and, where measurements allow, speed and completion estimates.
- Estimate disk-space needs and check usable space before installation. Keep temporary artifacts separate until verified; clean partial files safely.
- Cache shared libraries/assets, detect unused versions, and offer removal with clear data-loss warnings.
- Repair installations without silently deleting worlds, user configuration, mods, backups, or unrelated instances.
- Do not claim that an unlisted custom/modded version can be repaired by the vanilla-version workflow.

### 3. Instances and profiles

- Create vanilla and modded instances and instances from supported modpacks; clone, rename, favorite, tag, describe, icon, search, filter, sort, archive, restore, import, export and delete with confirmation.
- Keep game directories and per-instance mods/configurations/settings logically isolated.
- Display Minecraft and loader versions, last-played time, launch status, and installed mods.
- Configure per-instance Java runtime, safe memory allocation, launch/game arguments, resolution and only those graphics options the active runtime supports.
- Open launcher-owned or user-authorized instance directories through Android-approved APIs.
- Manage a profile's worlds, logs, screenshots, mods, resource packs and configuration files.
- Detect invalid/broken profiles; back up before risky updates; restore from backups without overwriting newer or conflicting user data.

### 4. Runtime and game execution

Inspect and retain the project's existing Amethyst-Android runtime integration. Do not presume a desktop JVM/game launcher can be executed unchanged on Android.

- Detect installed Java runtimes, Java version, ABI, native libraries, Minecraft compatibility, renderer and relevant runtime dependencies.
- Install or update runtimes from trusted, permitted sources; verify runtime integrity; repair or remove unused runtime files safely.
- Select Java globally and per instance when supported; configure heap within verified device-safe limits, JVM flags, classpath, game arguments and permitted environment values.
- Prepare native libraries and process configuration using the engine's supported mechanisms.
- Check support for ARM64, ARM32, x86 and x86-64 separately; the CI emulator's ABI alone does not prove physical-device compatibility.
- Evaluate Android sandbox limitations, OpenGL ES/Vulkan availability, translation layers, graphics drivers, RAM pressure, thermal throttling, and foreground/background lifecycle.
- Generate a compatibility report before launch. When a runtime, mod, loader, game version or native component is incompatible, identify the reason and alternatives.
- Launch asynchronously without freezing UI; show launch progress; prevent duplicate launches; safely stop the process; detect crashes; retain useful redacted logs; recover or return to launcher without corrupting files.

### 5. Launch workflow

Support one-tap launch for the selected instance, optional quick launch of the last-used instance, pre-launch validation, launch progress, supported fullscreen/immersive and orientation options, safe JVM/game argument editing, logs, session history, last-played tracking, crash detection, relaunch options, return-to-launcher behavior, and recovery after interruption. Do not report game success merely because the launcher activity opened.

### 6. Graphics, performance and battery

- Detect CPU architecture, device memory and available memory, graphics APIs, runtime, renderer and driver capability where Android APIs expose the data.
- Recommend RAM allocation within a conservative supported range; permit manual values only after validation.
- Provide automatic, low-memory, balanced and quality presets when the engine can faithfully implement them.
- Expose only real renderer, graphics translation, resolution/render-scale, FPS limit, VSync, battery-aware, shader and fullscreen settings.
- Warn about unsupported or experimental renderers; preserve a user's explicit settings when applying compatibility workarounds.
- Provide storage/cache cleanup, thermal/memory warnings where APIs permit, launch/crash diagnostics, and compatible optimization-mod suggestions.
- Suggest Sodium, Lithium, Iris, OptiFine and similar projects only when Minecraft version, loader, runtime, distribution rights and mobile compatibility metadata justify it.
- Do not advertise FPS gains or compatibility without measured or authoritative evidence.

### 7. Touch controls and hardware input

Support movement joystick or directional pad, jump, sneak/crouch, sprint, attack, use/interact, inventory, chat, hotbar selection, mouse emulation, look/swipe gestures, sensitivity, key remapping, and touch feedback through the engine's actual input system.

Provide a control layout editor with drag-to-position, size/opacity adjustment, show/hide toggles, presets, restore defaults, left-handed/Survival/PvP/Creative profiles where useful, per-instance/profile saving, portrait/landscape layouts, tablet-aware layouts, safe-area avoidance, and import/export where validated. Support external keyboards, mice, Bluetooth/USB input and gamepads where supported. Controller mapping, sensitivity, dead zones, shortcuts and profile management should reflect actual engine capabilities. Do not expose inert controls or promise gamepad/virtual-mouse behavior without end-to-end integration.

### 8. Mod loaders

Provide compatible documented install, update, selection, removal and repair workflows for Fabric, Forge, NeoForge, Quilt, supported legacy loaders, OptiFine and Iris where technically compatible. Display supported game/loader versions, required dependencies, installation logs and compatibility warnings. Manage loader state per instance and support safe profile migration. Desktop compatibility does not imply Android runtime compatibility.

### 9. Mod management

- Search legitimate providers, including Modrinth and supported CurseForge integration; filter by game version, loader, side/client/server applicability where metadata supports it.
- Show author, description, licensing, versions, provider/file metadata and images from permitted interfaces.
- Install, remove, enable/disable, update, select versions, import compatible local files, export mod lists and compare mods between instances.
- Resolve dependencies where provider metadata enables it; detect missing dependencies, duplicates, known conflicts and version mismatches.
- Back up changed files before updates and support rollback where safe.
- Warn about desktop-only mods, unsupported native code/APIs, side mismatch, incompatible loaders and unverified files.
- Respect provider API terms, license/distribution restrictions and authentication requirements. Never fabricate provider download URLs or bypass restricted-file requirements.

### 10. Modpacks

Browse/search/filter supported Modrinth, CurseForge and community packs; show descriptions, screenshots, pack/version metadata and mobile-runtime compatibility. Install isolated instances, import/export .mrpack and supported CurseForge/launcher archives, select versions, preview updates, preserve user additions where possible, back up before changes, roll back compatible updates, share legitimate pack links/files, and report restricted or missing files clearly.

Validate manifests, archive entries, URL schemes, file destinations, checksums and dependency metadata. Prevent zip-slip/path traversal, duplicate paths, symlink escapes and unbounded archive expansion. Do not assume a desktop pack runs merely because its Minecraft version matches.

### 11. Resource packs, shaders, datapacks and worlds

- Browse, import, enable/disable, remove and order resource packs where supported; validate ZIP structure and game compatibility.
- Manage shader packs and report renderer/GPU/runtime limitations; do not guarantee shader support.
- Support datapack workflows where the engine/game directory model permits them.
- Provide supported skin preview/selection without misrepresenting account authentication.
- List worlds and relevant metadata; import/export, back up, restore and delete worlds with confirmation; show compatibility warnings and identify duplicates when reliable.
- Access screenshots and supported Minecraft configuration files.
- Preserve worlds when repairing/updating versions. Never silently overwrite an existing world or newer configuration.
- Keep backup destinations and operations separate from live worlds; validate archive paths, file sizes, entry counts and checksums where available.

### 12. File management and Android storage

Provide a file manager for launcher-owned or user-authorized files: instances, mods, worlds, screenshots, resource packs, shader packs, config, logs, crash reports, backups and removable caches. Include search, sort, size display, rename/move/copy/delete with confirmation, supported text previews, open-with, ZIP import/export and storage usage.

Use Android Storage Access Framework and app-private storage appropriately. Clearly distinguish cache from game files, user data and backups. Protect runtime and essential files. Validate archive entries and destination paths; avoid broad storage permissions unless truly necessary and allowed by OS/store policy. Make export destinations and sharing explicit.

### 13. Authentication and accounts

Implement supported Microsoft OAuth and required Xbox/Minecraft authentication steps through the existing engine/provider flow. Verify entitlement where required, retrieve Minecraft profile information, manage multiple accounts and switching, handle session expiry/refresh/logout, display status, and provide practical troubleshooting. Store tokens securely using platform-supported protections, redact credentials/secrets from logs, and never collect Microsoft passwords directly.

Local/offline profiles are names for local use, not authenticated identities; they do not grant ownership, provide game files, or bypass authentication. Offer offline play only when legitimate and supported by the installed files and server rules. Never fabricate identity or bypass entitlement checks.

### 14. Multiplayer and community integrations

Manage server lists: add/edit/delete, names/icons/descriptions, favorites, recently joined servers, address validation, server list import/export, Quick Connect, latency/status/player count/version when publicly available, and profile-specific shortcuts. Show version/mod mismatch warnings and troubleshooting. Realms and Discord Rich Presence are optional, implemented only through supported integrations and appropriate privacy controls. Do not assume every status endpoint is reachable or every server allows client modifications.

### 15. Backups and recovery

Provide manual instance/world/config/mod backups; automatic backups before major risky updates where practical; selective backup; descriptive names/timestamps; compression; destination selection; size information; retention limits; integrity checks; restore previews; rollback and recovery from corrupt or incomplete instances; and launcher settings backup.

Check disk space, prevent archive path traversal, validate entry count/size/CRC when relevant, stage restores in temporary locations, and avoid overwriting existing worlds/configuration. Never silently replace newer local data. Cloud backups and synchronization are optional later phases: add authentication, encryption where appropriate, quota/error handling, conflict resolution, user consent, and explicit local-vs-cloud version choice before enabling them.

### 16. Central download manager

Manage multiple queued downloads, per-item and overall progress, reliable speed/remaining-time estimates, priority, concurrency and optional bandwidth limits, retries with backoff for transient failures, cancel/pause/resume only when the transfer supports it, duplicate suppression, integrity verification, temporary-file cleanup, destination visibility, disk estimates and task recovery after process restart. Optional throttling while playing can be added if it is reliable.

Respect Android network and background-work restrictions, connectivity changes, low storage and battery/resource pressure. Distinguish queued, active, paused, failed, canceled and completed status.

### 17. Notifications and background work

Provide opt-in or configurable notifications for completed/failed downloads, installations, updates, backups and relevant crashes. Respect Android notification permission, explain permission requests, cancel obsolete work, recover after process death where feasible, and avoid unnecessary continuously running services.

### 18. Settings and preferences

Organize global and per-instance settings for game version/default instance, Java/runtime, memory, graphics/renderer, resolution, fullscreen, touch controls, keyboard/controller, downloads, network, storage, cache, themes, language, accessibility, notifications, privacy, logs, backups, updates, developer diagnostics and reset-to-defaults. Validate types and ranges, preserve preferences across restarts, state when an option is unsupported, and avoid silently changing user-selected runtime settings.

### 19. UI, accessibility and localization

Use a consistent mobile-first Material-style design where it fits the current project. Provide dark/light themes, coherent typography/icons, bottom navigation or appropriate mobile drawer, search, cards, dialogs/confirmation sheets, loading skeletons, empty/error/retry states, accessible contrast, screen-reader labels, keyboard navigation, text scaling, reduced motion, localization and portrait/landscape/tablet/Chromebook adaptation. Keep game rendering separate from launcher navigation where runtime architecture requires it.

### 20. Security and privacy

- HTTPS for trusted remote operations; validate manifests and downloaded content.
- Safe ZIP extraction, path normalization, duplicate/collision detection, traversal and expansion limits.
- Validate URLs, input, filenames, numeric settings, imports and launch arguments.
- Safely handle processes and native libraries permitted by the app/runtime.
- Request the minimum Android permissions, explain their purpose, and honor OS/store rules.
- Secure token storage; redact secrets from logs, crash reports and diagnostic exports.
- Obtain consent for optional analytics/crash reporting; minimize collected data.
- Verify signed launcher updates where supported; protect extension points with explicit permission boundaries.
- Make file sharing, export, data deletion and privacy controls clear.
- Preserve upstream license notices and third-party attributions; review licenses and provider terms before redistributing code/assets or bundled files.

### 21. Optional advanced features

After core correctness: settings sync, encrypted/consent-based cloud backups, conflict-aware cross-device migration, instance/modpack sharing, shareable control layouts, mod dependency graph, compatibility reports, modpack update previews, automatic repair recommendations, playtime/session statistics, performance dashboard, community themes/layouts, permissioned plugins, automation/CLI on supported environments, custom launch workflows, widgets where supported, desktop-to-mobile profile migration, and Chromebook keyboard/mouse optimizations. These remain optional until the core install-launch-recovery path is reliable.

### 22. Testing and release requirements

#### Unit/regression tests
- Version metadata and inherited-version parsing.
- Runtime/ABI selection and compatibility rules.
- Launch configuration and argument construction.
- Settings persistence, validation, export/import/reset allowlists.
- Download retries, duplicate suppression and partial-file cleanup.
- Archive path/entry/size/CRC validation.
- Instance import/export and profile isolation.
- Backup create/verify/restore and no-overwrite behavior.
- Modpack manifest/provider metadata processing.
- Auth/session error handling and secret redaction.
- UI customization patch idempotency and expected-upstream safeguards.

#### Integration tests
- Install a supported Minecraft version and verify required metadata/files.
- Import a supported modpack; create and clone isolated instances.
- Detect unsupported runtime/loader/mod combinations.
- Recover after interrupted download, expired session, missing files and process restart.
- Restore from a backup without clobbering newer data.
- Verify that network/storage errors produce recoverable states.

#### Device acceptance
- Supported ARM64 and any separately claimed additional ABIs.
- Low-memory hardware, multiple screen sizes, portrait/landscape and tablet layouts.
- Physical keyboard, mouse and gamepad where claimed.
- Storage/notification permission denial, network disconnect, low disk, app backgrounding, process kill, runtime crash, memory and thermal pressure.
- Actual selected-profile navigation, dialogs, file import/export, authentication, installation, world launch, touch controls, graphics/rendering and performance.

#### Build validation
Run existing regression tests, Python compilation, shell syntax checks, lint/static analysis available in the project, Gradle/runtime build, APK package/signature/SDK/ABI checks, and an Android emulator install/launch smoke test when the environment supports it. Record missing tools, secrets, devices or tests that could not be run.

The existing hosted emulator test proves package installation and launcher foreground startup; it does not prove gameplay, physical ABI support, account sign-in, modpack installation, graphics quality, or actual world launch.

### 23. Phased delivery roadmap

1. **Repository and runtime audit:** inspect the existing repo/branch, source patcher, pinned upstream, toolchain, minimum/target SDK, app manifest, ABI filters/native components, authentication, storage, launcher screens and actual test baseline.
2. **Core mobile experience:** reliable navigation, responsive dashboard, preferences, profile/instance model, data isolation, task statuses, file/error recovery.
3. **Installation/runtime/launch reliability:** version metadata, download integrity and resume, runtime compatibility, safe launch configuration, process handling, logs and crash recovery.
4. **Authentication:** complete supported OAuth/entitlement/session lifecycle and secure token handling.
5. **Touch, hardware input and graphics:** audit inherited controls, implement missing editor/profile needs, validate renderer/ABI/device memory, and test real gameplay.
6. **Loaders and mods:** compatible loader installers, mod provider integration, metadata, dependency and conflict checks, backup/rollback.
7. **Modpacks, content and world safety:** validated import/export, resource/shader/world management, backups and recovery.
8. **Advanced mobile UX:** servers, central task management, notifications, settings migration, richer diagnostics, optional cloud/plugin work.
9. **Release readiness:** full regression matrix, real-device/gameplay acceptance, accessibility/privacy/security review, license audit, release packaging, rollback and documentation.

Reorder work only after repository findings or dependency constraints justify doing so. Do not implement later-phase features by weakening safety or skipping baseline tests.

### 24. Definition of done

A feature is done only when all applicable points are true:
- Real behavior is integrated into the existing project—not just a mockup or placeholder.
- The UI reports actual operation state and performs the advertised action.
- Inputs, permissions, cancellation, exceptions and error/retry states are handled.
- Instance, world, account and configuration data are preserved correctly.
- Security, privacy, provider terms, licensing and Android platform restrictions are respected.
- Relevant tests are added or updated and actually run; results and unrun tests are documented.
- Build/package checks succeed where the environment permits.
- Compatibility claims have evidence for the specified Minecraft version, runtime, ABI, device and renderer.
- Documentation agrees with the implementation and known limitations remain visible.

When blocked by credentials, unavailable services, hardware or platform constraints, document the blocker and continue independent work. Never claim code changed, a build passed, or Minecraft launched unless the result is verified.

### 25. Required project documentation

Maintain and cross-link:
- FEATURE_AUDIT.md — source-evidenced implemented/partial/missing/broken/blocked/not-applicable statuses.
- IMPLEMENTATION_PLAN.md — priorities, dependencies, phases, acceptance criteria and next task.
- ARCHITECTURE.md — current repository/upstream boundary, data/task flows, runtime and storage constraints.
- TESTING.md — commands, automation coverage, acceptance matrix and known testing gaps.
- CHANGELOG.md — auditable changes and verification results.
- FEATURE_ROADMAP.md — broad scope and evolving status table.
- README.md — build, installation, compatibility, security and user troubleshooting entry point.

These documents are project planning artifacts; none makes an implementation claim on its own.
