# VoidCraft architecture (migration work in progress)

## Current implementation

The independent app is in `voidcraft-app/`, uses Kotlin and Android platform Views, and has its own Android application ID: `com.voidcraft.launcher`.

Implemented:
- `MainActivity`: independently authored dashboard and navigation; no upstream Activity or UI code is imported.
- `InstanceListActivity`: profile metadata list/create/remove UI; removal is non-destructive.
- `VersionBrowserActivity`: fetches official Mojang version metadata on a background executor, filters/searches the catalog, fetches details on selection, and creates profile metadata only.
- `core/InstanceProfile.kt` and `core/InstanceCatalogRepository.kt`: validated profile model and versioned app-private catalog.
- `core/MinecraftVersionCatalog.kt`: manifest/detail data models, library declarations/rules, official manifest client, version-detail integrity checks, and asset-index fetch/parsing.
- `core/MinecraftVersionInheritanceResolver.kt`: cycle/depth-checked parent resolution, child-over-parent scalar fallback, library coordinate overrides, and inherited client-directory tracking.
- `core/MinecraftVersionInstallPreparationService.kt`: resolves inheritance, fetches/verifies the effective asset index, and constructs an install plan without downloading game artifacts.
- `core/MinecraftInstallPlanner.kt`: asset-index parser, ordered library rule evaluator, platform-specific classifier selection, Maven path validation, and a deduplicated download plan with explicit warnings for unresolved prerequisites.
- `core/SecureFileDownloader.kt`: bounded HTTPS transfer with cancellation, transient retries, supplied hash/size checks and safe per-file replacement.
- `core/MinecraftInstallVerifier.kt`: read-only integrity scan of planned files, with explicit missing/size/hash/verified/unverifiable statuses.
- `core/MinecraftInstallExecutor.kt`: complete-plan-only execution, reuse of verified cached files, staged downloads, transactional promotion, backup, and rollback on commit failure.
- `core/MinecraftArtifactInstallService.kt`: coordinates metadata preparation with the verified artifact executor, exposes install stages/progress, and rejects incomplete or mismatched plans before downloads.
- `core/MinecraftNativeArchiveExtractor.kt` and `core/MinecraftNativeLibraryAssembler.kt`: bounded ZIP extraction, traversal rejection, metadata exclusions, cancellation cleanup, multi-archive assembly, and conflict checks for duplicate files.
- `core/MinecraftLaunchArgumentInterpolator.kt`: placeholder expansion without re-tokenizing arguments; combines with existing metadata rule filtering while rejecting unresolved placeholders.
- Unit tests cover catalog safety, metadata integrity, library/asset planning, downloader integrity/cancellation/retries, and install execution safety.

Not yet implemented or connected:
- Complete version installation UI and launch-context assembly (modern argument values can now be filtered and interpolated, but final game/JVM arguments, classpath, logging, account context, legacy arguments, and loader-specific metadata are not wired end to end).
- Integration of native extraction/assembly with downloaded artifacts and the runtime launch directory; Java runtime acquisition.
- Resumable downloads and a persistent download queue.
- Real Android runtime platform adaptation and native library compatibility.
- Microsoft/Xbox/Minecraft authentication and secure token storage.
- Java/native runtime launch bridge, renderer/input integration.
- Import/export, modpack workflows, touch-control designer, and feature store.
- Read-only discovery and migration of data from the existing launcher.

The Versions screen only browses metadata and creates profile records; it does not start installation. The artifact-install orchestrator, native extractor/assembler, and argument resolver are internal core services and are not connected to the UI or a real process-launch operation. Performance, runtime launch, auth, and feature-store buttons remain placeholders.

## Data-safety invariants

1. Removing a catalog record never deletes an instance directory or world.
2. Unknown catalog schemas fail visibly instead of being overwritten.
3. Metadata and asset indexes are checked against manifest-provided hashes when available.
4. Planned paths must be relative, traversal-free, unique, restricted to the expected versions/libraries/assets tree, and consistent with their artifact kind.
5. Existing files are reused only when the authoritative SHA-1 matches and any declared byte size matches; length-only files without a hash are not trusted as verified.
6. An inherited client JAR is stored under the version that supplies the client download while the selected child version's JSON remains under its own version directory.
7. Newly required files download to a staging tree first; no final file changes until all required downloads have completed and passed verification.
8. Existing files are backed up during commit. If commit fails, changes are rolled back in reverse order; if rollback fails, the staging/recovery tree is retained.
9. Every planned URL is revalidated against approved HTTPS distribution hosts before the executor makes network requests.
10. The existing launcher and build path remain available until feature, runtime, license and device parity gates pass.

## Platform warning

The platform context for library-rule evaluation must describe the intended Minecraft runtime/platform, not blindly use Android's OS name. ARM64/ARM32 classifier mapping and renderer/runtime compatibility need a dedicated adapter and device tests before the generated plan is executed for an Android target.
