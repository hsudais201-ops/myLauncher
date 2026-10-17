# VoidCraft Launcher feature scope and delivery tracker

**Owner / developer: MF**

## Important status rule

This document tracks the complete requested feature scope. A feature appearing in this list does **not** by itself mean the feature is implemented or has passed a runtime test. The project builds the pinned [Amethyst-Android engine](https://github.com/AngelAuraMC/Amethyst-Android) and applies VoidCraft Launcher patches; existing engine features should be audited and exposed before reimplementing them. Every unverified item below needs a source check and/or an acceptance test before it is described as shipped.

## Current source audit

| Area | Current evidence | Status |
|---|---|---|
| Account flow | Upstream has Microsoft login, account selector, local-login screen, account/profile classes; VoidCraft Launcher removes the online-profile gate for local-account creation and the offline-profile download gate. | Source patched; account UI still needs emulator/device acceptance testing. |
| Minecraft versions/downloads | Upstream has version-list, old-version utilities, async downloader, version metadata classes, SHA-1/download utilities, mirrors and asset handling. | Inherited foundation; VoidCraft Launcher now permits downloads for local accounts when connected and adds missing/unreadable/empty version-JSON checks with plain-language recovery guidance plus regression tests. Actual version download/repair still needs runtime validation. |
| Mod loaders/modpacks | Source includes Forge, NeoForge, Fabric-like, OptiFine, Modrinth and CurseForge components. | Partial source-confirmed foundation; support for each requested loader/action must be checked individually. |
| Java / launch / rendering | Source includes Java 8/17/21 runtime components, launch activity, GLFW/OpenGL environment, renderer preferences and native/input components. | Base engine inherited; per-version compatibility and device rendering need acceptance tests. |
| Touch and controllers | Source includes a custom-controls system, touch/mouse/keyboard helpers and gamepad mapping classes. | Base engine inherited; all requested editor/layout/profile options still need audit. |
| Performance | VoidCraft Launcher patch changes low-memory heap defaults (296/448/512 MB tiers for devices up to 2 GB RAM) and targets about a 480 px short screen edge for that tier. | Customization source-reviewed and APK-build verified; device performance is not guaranteed. |
| Launcher UI / folders | Custom home header, Add account shortcut, vector icon, portrait/landscape layouts, and profile-specific Mods, Resource packs, World saves, Game logs, and Screenshots shortcuts are merged to `main`; the Shader packs shortcut is in the current feature branch. Each creates and opens the selected profile's `mods/`, `resourcepacks/`, `saves/`, `logs/`, or `screenshots/` directory and preserves demo-profile protection. | PR run [#38996198756](https://github.com/hsudais201-ops/myLauncher/actions/runs/38996198756) passed tests, APK build/signature/package validation, and Android 9 emulator install/launch. Post-merge main run [#38996576044](https://github.com/hsudais201-ops/myLauncher/actions/runs/38996576044) is in progress. Folder-button taps and broader accessibility/device acceptance remain. |
| Logs / settings | Upstream has Logger/LoggerView and settings fragments/preferences. | Base functionality inherited; requested diagnostic explanations, automatic repair and reset/import/export details need audit. |
| Packaging / CI | GitHub Actions runs Python regression tests, fetches the pinned engine and submodules, applies patches, builds the debug APK, verifies package metadata/signature, writes SHA-256, installs and launches in a headless Android 9/API 28 emulator, and uploads APK/build/diagnostic artifacts. | Mods main run [#38992817171](https://github.com/hsudais201-ops/myLauncher/actions/runs/38992817171), Resource packs PR run [#38995241453](https://github.com/hsudais201-ops/myLauncher/actions/runs/38995241453), and World saves PR run [#38996198756](https://github.com/hsudais201-ops/myLauncher/actions/runs/38996198756) passed. The Game logs shortcut passed build, package validation, and Android 9 emulator launch in run [#39050369162](https://github.com/hsudais201-ops/myLauncher/actions/runs/39050369162). The Screenshots shortcut passed run [#39050991062](https://github.com/hsudais201-ops/myLauncher/actions/runs/39050991062), including APK build and Android 9 emulator launch. World backup and restore passed run [#39052725546](https://github.com/hsudais201-ops/myLauncher/actions/runs/39052725546). The Shader packs shortcut is on a feature branch awaiting verification. Post-merge main run [#38996576044](https://github.com/hsudais201-ops/myLauncher/actions/runs/38996576044) is in progress. In-game and physical-device acceptance remain. |
| Profiles/world backups/cloud sync/updates/plugin APIs | The selected-profile `saves/` folder is available. **World saves** now offers folder access or ZIP restoration on tap and timestamped ZIP backup on long-press. Restore extracts to a temporary directory, rejects unsafe/duplicate paths and archives over 20 GB uncompressed, then restores names without overwriting existing worlds. Backup and restore run off the UI thread and remove staging files on failure. Cloud sync, scheduled backups, updates, and plugin APIs remain unimplemented. | Backup and restore are in the current feature branch awaiting regression/build/emulator verification. |

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
- Log export
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
- Plain-language error explanations
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
