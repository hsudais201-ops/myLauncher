# VoidCraft Launcher feature scope and delivery tracker

**Owner / developer: MF**

## Important status rule

This document tracks the complete requested feature scope. A feature appearing in this list does **not** by itself mean the feature is implemented or has passed a runtime test. The project builds the pinned [Amethyst-Android engine](https://github.com/AngelAuraMC/Amethyst-Android) and applies VoidCraft Launcher patches; existing engine features should be audited and exposed before reimplementing them. Every unverified item below needs a source check and/or an acceptance test before it is described as shipped.

## Current source audit

| Area | Current evidence | Status |
|---|---|---|
| Account flow | Upstream has Microsoft login, account selector, local-login screen, account/profile classes; VoidCraft Launcher removes the online-profile gate for local-account creation and the offline-profile download gate. | Source patched; account UI still needs emulator/device acceptance testing. |
| Minecraft versions/downloads | Upstream has version-list, async downloader, checksum-aware downloads, mirrors and asset handling. The home screen now has **Verify / repair version**, which confirms intent, requires internet, resolves the selected profile against the official version list, reuses the existing downloader, and reports completion/failure without launching Minecraft. Missing/unreadable/empty selected-version and required `inheritsFrom` parent JSON guards remain in place; offline username validation uses full-string matching and submits the validated field value. | Regression coverage checks the button, both layouts, official-version guard and no-launch callback. Actual network repair of a missing/corrupt client or 26.x manifest still needs device acceptance. Custom/unlisted modded versions are intentionally rejected by this shortcut. |
| Mod management | Mods tap opens the selected profile's folder; long-press lists top-level `.jar` and `.jar.disabled` files, labels their state, and confirms an enable/disable rename without overwriting a conflicting destination. | The implementation and regression tests are on `main`; the APK build/package checks passed in main run [#39101148914](https://github.com/hsudais201-ops/myLauncher/actions/runs/39101148914). The cloud emulator verifies launcher startup, not tapping this menu, so manual device acceptance is still needed. Mod installing, updating, conflict detection, and compatibility checks remain unimplemented. |
| Mod loaders/modpacks | Source includes Forge, NeoForge, Fabric-like, OptiFine, Modrinth and CurseForge components. Top-level JAR mods and ZIP resource packs have safe enable/disable dialogs via long-press, with confirmation and conflict checks. | Source and regression coverage added; dialog interaction still needs manual device acceptance. Installers, dependency conflicts, updates and compatibility checks remain unimplemented. |
| Java / launch / rendering | Source includes Java 8/17/21 runtime components, launch activity, GLFW/OpenGL environment, renderer preferences and native/input components. A version-gated MobileGlues partial error-filter setting is applied for affected Minecraft 26.3 IDs only when the saved value remains default. | Experimental source patch and regression checks; the affected game must still be tested on-device. The workaround cannot guarantee compatibility or address missing Vulkan capabilities and other upstream graphics failures. Full gameplay still needs acceptance testing. |
| Touch and controllers | Source includes a custom-controls system, touch/mouse/keyboard helpers and gamepad mapping classes. | Base engine inherited; all requested editor/layout/profile options still need audit. |
| Performance | VoidCraft Launcher patch changes low-memory heap defaults (296/448/512 MB tiers for devices up to 2 GB RAM) and targets about a 480 px short screen edge for that tier. | Customization source-reviewed and APK-build verified; device performance is not guaranteed. |
| Launcher UI / folders | Shared controls, inputs, dialogs, and both portrait/landscape home screens now use a coordinated night-violet theme and VoidCraft Launcher hero panel. Profile-specific Mods, Resource packs, World saves, Game logs, Screenshots, Crash reports, Config, and Shader packs shortcuts open/create their selected-profile directories. Mods, Resource packs and Shader packs have safe long-press toggles; Game logs copies a bounded redacted excerpt; Crash reports prepares a bounded redacted report in Android's share chooser. | UI styling has isolated source regression coverage and is included in the Android build workflow. Folder shortcuts expose directories; shader/mod compatibility and individual dialogs still need manual-device acceptance. Shader-pack ZIP toggling is source-patched with path and collision guards. |
| Logs / settings | Upstream Logger/LoggerView provides case-insensitive live-log filtering, clear, match counts and bounded retention. Game logs supports bounded redacted copy/share and heuristic diagnosis. Config folder supports a long-press versioned JSON export/import workflow that allowlists portable primitive preferences, caps import reads at 64 KiB, rejects unknown keys and mismatched types, confirms before applying, and limits imported RAM allocation to a device-safe recommendation. Account/session data, profile identity, free-form Java args, control-file paths and world data are excluded. | Source regression coverage checks log bounds/redaction, diagnosis signatures, safe settings export exclusions, and bounded/confirmed settings import. Full-preference backup and physical-device share-sheet/document-picker acceptance remain unverified. |
| Packaging / CI | GitHub Actions runs Python regression tests, fetches the pinned engine and submodules, applies patches, builds the debug APK, verifies package metadata/signature, writes SHA-256, installs and launches in a headless Android 9/API 28 emulator, and uploads APK/build/diagnostic artifacts. | Mods main run [#38992817171](https://github.com/hsudais201-ops/myLauncher/actions/runs/38992817171), Resource packs PR run [#38995241453](https://github.com/hsudais201-ops/myLauncher/actions/runs/38995241453), and World saves PR run [#38996198756](https://github.com/hsudais201-ops/myLauncher/actions/runs/38996198756) passed. The Game logs shortcut passed build, package validation, and Android 9 emulator launch in run [#39050369162](https://github.com/hsudais201-ops/myLauncher/actions/runs/39050369162). The Screenshots shortcut passed run [#39050991062](https://github.com/hsudais201-ops/myLauncher/actions/runs/39050991062), including APK build and Android 9 emulator launch. World backup and storage-aware ZIP restoration passed run [#39052725546](https://github.com/hsudais201-ops/myLauncher/actions/runs/39052725546), including the Android 9 emulator install-and-launch check. The latest post-merge main run [#39106117976](https://github.com/hsudais201-ops/myLauncher/actions/runs/39106117976) passed on October 18, 2026, on commit `ffbaa3c27a1300e6391f0ea885e1adf1c1dbe0da` after world-backup picker metadata was merged, including regression tests, APK build/package/signature checks, Android 9 emulator install/launch, and artifact uploads. The picker metadata change passed PR workflow [#39102041594](https://github.com/hsudais201-ops/myLauncher/actions/runs/39102041594). Emulator foreground verification and failure diagnostics were hardened in PR [#43](https://github.com/hsudais201-ops/myLauncher/pull/43). Safe ZIP-backup deletion passed its PR workflow [#39096049815](https://github.com/hsudais201-ops/myLauncher/actions/runs/39096049815) and the post-merge main workflow passed after PR [#48](https://github.com/hsudais201-ops/myLauncher/pull/48). In-game and physical-device acceptance remain. |
| Profiles/world backups/cloud sync/updates/plugin APIs | The selected-profile `saves/` folder is available. **World saves** offers folder access, ZIP restoration, read-only ZIP archive verification, and confirmed deletion of a selected backup on tap, plus timestamped ZIP backup on long-press. Backup pickers display filename, readable size, and last-modified time and sort entries newest first. Verification reads entries fully to surface ZIP data/CRC errors without extracting or modifying files, rejects unsafe/duplicate/conflicting paths, and caps scans at 250,000 entries and 20 GiB uncompressed. Deletion verifies the canonical selected-profile `backups/` paths and never removes restored worlds. Restore extracts to a temporary directory, rejects unsafe/duplicate paths and archives over 20 GB uncompressed, then restores names without overwriting existing worlds. Backup and restore run off the UI thread and remove staging files on failure. Cloud sync, scheduled backups, updates, and plugin APIs remain unimplemented. | World backup and restore are merged in PR #12 and PR #15; run [#39052725546](https://github.com/hsudais201-ops/myLauncher/actions/runs/39052725546) passed regression checks, APK build/package checks, and Android 9 emulator installation/launch. Confirmed ZIP-backup deletion was added in PR [#48](https://github.com/hsudais201-ops/myLauncher/pull/48); run [#39096049815](https://github.com/hsudais201-ops/myLauncher/actions/runs/39096049815) passed regression tests, APK/package/signature checks, Android 9 launch, and artifact uploads. PR [#51](https://github.com/hsudais201-ops/myLauncher/pull/51) adds filename, readable size, and last-modified time in restore/verify/delete pickers; [run #39102041594](https://github.com/hsudais201-ops/myLauncher/actions/runs/39102041594) passed, followed by passing post-merge main run [#39106117976](https://github.com/hsudais201-ops/myLauncher/actions/runs/39106117976). Deletion validates the canonical selected-profile backup paths and rejects symlink aliases. Cloud sync, scheduled backups, updates, and plugin APIs remain unimplemented. |

## Source files inspected for the initial audit

These links point to the pinned upstream source that the build uses. They establish that these implementation areas exist, not that every feature in their area works on every device or is exposed through a dedicated VoidCraft Launcher screen.

- **Accounts:** [MicrosoftLoginFragment](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MicrosoftLoginFragment.java), [LocalLoginFragment](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/LocalLoginFragment.java), [mcAccountSpinner](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/com/kdt/mcgui/mcAccountSpinner.java), [MinecraftAccount](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/value/MinecraftAccount.java).
- **Versions and downloads:** [JMinecraftVersionList](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/JMinecraftVersionList.java), [MinecraftDownloader](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/tasks/MinecraftDownloader.java), [AsyncMinecraftDownloader](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/tasks/AsyncMinecraftDownloader.java), [OldVersionsUtils](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/OldVersionsUtils.java), [DownloadUtils](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/DownloadUtils.java), [DownloadMirror](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/mirrors/DownloadMirror.java).
- **Loaders and modpacks:** [ForgeDownloadTask](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/ForgeDownloadTask.java), [NeoForgeDownloadTask](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/NeoForgeDownloadTask.java), [FabriclikeDownloadTask](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/FabriclikeDownloadTask.java), [OptiFineDownloadTask](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/OptiFineDownloadTask.java), [ModrinthApi](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/ModrinthApi.java), [CurseforgeApi](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/CurseforgeApi.java).
- **Java, controls and rendering:** [MultiRTConfigDialog](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/multirt/MultiRTConfigDialog.java), [CustomControls](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/customcontrols/CustomControls.java), [GamepadMapStore](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/customcontrols/gamepad/GamepadMapStore.java), [LauncherPreferenceRendererSettingsFragment](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/screens/LauncherPreferenceRendererSettingsFragment.java).
- **Logs and settings:** [LoggerView](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/com/kdt/LoggerView.java), [Logger](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/Logger.java), [LauncherPreferenceFragment](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/screens/LauncherPreferenceFragment.java).

## Delivery phases

1. **Stability and installability** — offline account download, missing/corrupt JSON handling, download integrity/error recovery, APK install/launch smoke tests, and regression coverage.
2. **Expose and verify inherited engine features** — accounts, versions, loaders, runtimes, renderer/input, controls, settings, logs and modpack flow; avoid duplicating upstream code.
3. **Profiles and content workflows** — isolated instances, profile import/export/backup, world/resource-pack/file management and safe deletion.
4. **Missing launcher-native experiences** — multiplayer/server manager, download queue controls, readable diagnostics, update channels, backup/sync and advanced plugin APIs.
5. **Release readiness** — Android lint, emulator/device matrix, accessibility, compatibility, dependency/licensing review, release build verification and rollback tests.

---

## 1. Account Management

- Microsoft account login
- Xbox authentication
- Minecraft ownership verification
- Multiple accounts
- Account switching
- Secure token storage
- Automatic token refresh
- Account logout
- Offline account login
- Offline username selection
- Player UUID management
- Guest/demo mode
- Account profile and avatar
- Account session validation
- Authentication error handling

## 2. Minecraft Version Management

- Release version downloads
- Snapshot version downloads
- Historical version support
- Version selection
- Version installation
- Version deletion
- Version repair
- Version metadata validation
- Automatic dependency downloads
- Minecraft asset downloads
- Library downloads
- Download verification
- Download retry
- Download pause and resume
- Download progress tracking
- Download cancellation
- Download history
- Version manifest management
- Automatic version detection
- Installation integrity checks

## 3. Mod Loaders

- Vanilla Minecraft
- Forge support
- NeoForge support
- Fabric support
- Quilt support
- OptiFine support
- Legacy Forge support
- LiteLoader support
- Cleanroom support
- Loader version selection
- Loader installation
- Loader updating
- Loader removal
- Loader compatibility checks
- Loader dependency management

## 4. Mod Management

- Mod installation
- Mod removal
- Mod enabling and disabling
- Mod updating
- Mod version information
- Mod search
- Mod categorization
- Mod dependency detection
- Mod conflict detection
- Duplicate mod detection
- Mod compatibility checking
- Mod file import
- Mod file export
- Mod descriptions and author information
- Modpack management
- Modrinth integration
- CurseForge integration
- Modpack ZIP import
- Modpack export
- Modpack updates

## 5. Game Profiles and Instances

- Create game profiles
- Delete game profiles
- Rename game profiles
- Duplicate game profiles
- Clone existing instances
- Separate game directories
- Separate mods per profile
- Separate configurations per profile
- Profile icons
- Profile descriptions
- Profile folders
- Profile tags
- Profile favorites
- Profile search
- Profile sorting
- Profile import
- Profile export
- Profile backup
- Profile restoration
- Last-played tracking

## 6. Java Runtime Management

- Java runtime installation
- Java 8 support
- Java 17 support
- Java 21 support
- Newer Java version support
- Custom Java runtime import
- Automatic Java selection
- Manual Java selection
- Java version detection
- Java architecture detection
- Java compatibility checks
- Java runtime validation
- JVM argument configuration
- Java startup error detection
- Java runtime repair

## 7. Memory and Performance

- Minimum heap memory settings
- Maximum heap memory settings
- Automatic memory recommendations
- Available RAM monitoring
- Low-memory warnings
- Garbage collector configuration
- CPU architecture detection
- Performance presets
- Battery-saving mode
- Frame-rate limiting
- Resolution scaling
- Storage usage monitoring
- Temporary file cleanup
- Performance diagnostics
- Memory crash detection

## 8. Graphics and Rendering

- OpenGL ES support
- Graphics renderer selection
- VirGL support
- Zink support
- Renderer plugin support
- Renderer configuration
- Graphics compatibility checks
- Custom game resolution
- Fullscreen mode
- Immersive mode
- VSync configuration
- Shader support
- Graphics presets
- Renderer error diagnostics
- Graphics debug overlay
- Renderer switching
- Resolution presets
- Frame-rate settings
- Graphics driver diagnostics
- Rendering compatibility warnings

## 9. Touchscreen Controls

- Virtual movement joystick
- Directional movement buttons
- Jump button
- Crouch button
- Sprint button
- Attack button
- Interact button
- Inventory button
- Chat button
- Sneak toggle
- Mouse emulation
- Virtual keyboard
- Touch camera controls
- Swipe-to-look controls
- Adjustable button positions
- Adjustable button sizes
- Button transparency
- Custom control layouts
- Multiple control profiles
- Control layout import
- Control layout export
- Control layout reset
- Left-handed layouts
- Survival control presets
- PvP control presets
- Creative control presets
- Live control editor
- Hideable on-screen controls

## 10. Keyboard, Mouse and Controllers

- Physical keyboard support
- Physical mouse support
- Bluetooth keyboard support
- Bluetooth mouse support
- USB keyboard support
- USB mouse support
- Gamepad support
- Controller button mapping
- Controller sensitivity settings
- Controller dead-zone settings
- Keyboard shortcut customization
- Mouse sensitivity
- Camera sensitivity
- Automatic touchscreen control hiding
- Controller profile management

## 11. Worlds and Saves

- World list
- World thumbnails
- World information
- World import
- World export
- World backup
- World restoration
- World deletion
- World renaming
- World duplication
- World version information
- World storage management
- Saves folder access
- World compatibility warnings

## 12. Resource Packs and Game Files

- Resource pack management
- Shader pack management
- Data pack management
- Screenshot management
- Configuration file management
- ZIP file import
- Resource pack activation
- Resource pack removal
- Resource pack compatibility checks
- File browser
- Minecraft directory access
- Storage usage information
- Safe file deletion

## 13. User Interface

- Modern home screen
- Dark theme
- Light theme
- Custom accent colors
- Custom background images
- Custom launcher logo
- Custom application icon
- Profile cards
- Recently played section
- Favorite profiles section
- Quick-launch button
- Search bar
- Download manager screen
- Account switcher interface
- Settings search
- Customizable home screen
- Language selection
- Localization
- Accessibility settings
- Adjustable text size
- Portrait layout
- Landscape layout
- Tablet layout
- Chromebook support
- Custom fonts
- Reset interface settings

## 14. Download Manager

- Download queue
- Parallel downloads
- Download concurrency settings
- Download speed display
- Download progress percentage
- Remaining download size
- Download time estimation
- Pause downloads
- Resume downloads
- Cancel downloads
- Automatic download retry
- Network timeout handling
- Download mirrors
- Proxy configuration
- Wi-Fi-only downloads
- Background downloads
- Download notifications
- Download verification
- Download cache
- Duplicate download prevention
- Network connectivity checks
- Download error reporting

## 15. Game Launching

- Launch Minecraft
- Quick launch last-used profile
- Launch selected version
- Launch validation checks
- Java selection for launching
- Classpath generation
- Library dependency resolution
- Native library extraction
- JVM argument generation
- Minecraft argument generation
- Game directory configuration
- Authentication argument handling
- Launch progress display
- Game process monitoring
- Game stop control
- Duplicate launch prevention
- Successful startup detection
- Crash detection
- Automatic return to launcher
- Launch history
- Launch configuration saving
- Launch error reporting

## 16. Multiplayer

- Server list
- Add servers
- Edit servers
- Delete servers
- Server favorites
- Server icons
- Server address copying
- Quick server connection
- Recent servers
- Server ping display
- Server status checking
- Server version detection
- Server list import
- Server list export
- Profile-specific server lists
- Multiplayer compatibility warnings

## 17. Logs and Crash Diagnostics

- Live log viewer
- Log search
- Log filtering
- Log copying
- Log export (copy or share the latest game log with sensitive-token redaction)
- Crash report generation
- Missing JSON detection
- Missing library detection
- Missing asset detection
- Invalid metadata detection
- Java incompatibility detection
- Mod conflict diagnostics
- Out-of-memory detection
- Renderer failure detection
- Storage permission error detection
- Network error diagnostics
- Plain-language error explanations (basic latest-log triage implemented; broader diagnostics remain planned)
- Automatic repair suggestions
- Installation integrity checks
- Safe mode
- Diagnostic report sharing
- Regression error tracking

## 18. Settings and Customization

- Launcher language settings
- Default Minecraft version
- Default account
- Default game directory
- Global Java settings
- Per-profile Java settings
- Global memory settings
- Per-profile memory settings
- Custom JVM arguments
- Custom game arguments
- Environment configuration
- Automatic update checks
- Update notifications
- Settings import
- Settings export
- Individual settings reset
- Restore default settings
- Developer mode
- Debug logging
- Storage location settings
- Privacy settings
- About page

## 19. Security and Privacy

- Secure account token storage
- Password protection
- Official authentication flows
- Authentication token redaction
- Download integrity verification
- Untrusted mod warnings
- Destructive action confirmation
- Secure logout
- Private file protection
- Backup protection
- Dependency updates
- Permission explanations
- Privacy-friendly diagnostics
- Open-source license acknowledgments

## 20. Updates and Maintenance

- Launcher update checker
- Update notifications
- Release notes
- Stable release channel
- Development release channel
- Update integrity verification
- Configuration migration
- Settings preservation
- Automatic backup before migration
- Update failure reporting
- Safe rollback support
- Dependency compatibility checks

## 21. Backup and Cloud Features

- Profile backups
- World backups
- Settings backups
- Backup restoration
- Cloud backup integration
- Cross-device synchronization
- Profile sharing
- Modpack manifest sharing
- Control layout sharing
- Diagnostic report sharing
- Backup scheduling
- Backup storage management
- Backup integrity verification

## 22. Advanced Features

- Renderer plugin API
- Custom control plugins
- Mod compatibility reports
- Dependency conflict visualization
- Modpack update previews
- Profile comparison
- Launch configuration comparison
- Advanced diagnostics
- Performance benchmarks
- External tool integration
- Experimental compatibility settings
- Custom themes
- Custom profile templates
- Automated repair system
- Compatibility database
- Advanced graphics diagnostics

## 23. Developer Tools and Testing

- Gradle build verification
- Unit tests
- Offline account tests
- Authentication tests
- Version metadata tests
- Missing JSON regression tests
- Download retry tests
- Java selection tests
- Classpath generation tests
- Game argument tests
- Profile isolation tests
- Android lint checks
- APK packaging verification
- Automated APK installation tests
- Android emulator launch tests
- Crash detection tests
- Test log collection
- GitHub Actions integration
- Automated APK artifacts
- Regression testing
- Accessibility testing
- Release build verification
- Automated compatibility tests
- Automated installation repair tests
- Build failure diagnostics
