# VoidCraft architecture (migration work in progress)

## Current implementation

The independent app is in `voidcraft-app/`, uses Kotlin and Android platform Views, and has its own Android application ID: `com.voidcraft.launcher`.

Implemented:
- `MainActivity`: independently authored dashboard and navigation; no upstream Activity or UI code is imported.
- `InstanceListActivity`: profile metadata list/create/remove UI, with explicit messaging that game files are not installed and removal is non-destructive.
- `VersionBrowserActivity`: fetches official Mojang version metadata on a background executor, filters/searches the catalog, fetches details on selection, and creates profile metadata only.
- `core/InstanceProfile.kt`: validated metadata model for one game instance.
- `core/InstanceCatalogRepository.kt`: versioned, app-private metadata catalog with temporary-file writes, schema validation, and metadata-only removal.
- `core/MinecraftVersionCatalog.kt`: manifest/detail data models, library declarations/rules, official manifest client, version-detail and asset-index integrity checks, HTTPS host/redirect checks, and bounded reads.
- `core/MinecraftInstallPlanner.kt`: asset-index object parser, ordered library rule evaluator, platform-specific classifier choice, safe Maven path resolution, deduplicated version/client/library/native/asset download plan with unresolved warnings.
- `core/SecureFileDownloader.kt`: HTTPS file transfer with bounded size, progress callback, cancellation, transient retries, optional hashes, unique temporary-file staging, and validated destination replacement.
- Unit tests cover catalog safety, metadata SHA-1, asset/libraries/rules/path planning, and downloader integrity/cancellation/retries.

Not yet implemented or connected:
- Download queue orchestration and actual staged installation/repair UI.
- Resumable downloads and a persistent queue.
- Recursive resolution/merging of inherited version metadata.
- Real Android runtime platform adaptation and native library compatibility for Android.
- Microsoft/Xbox/Minecraft authentication and secure token storage.
- Java runtime and native runtime launch bridge.
- Renderer/input integration.
- Import/export, modpack workflows, touch-control designer, and feature store.
- Read-only discovery and migration of data from the existing launcher.

The Instances dashboard button opens the metadata-only profile screen. The Minecraft versions button opens the metadata browser; it does not install game files. Performance, runtime launch, authentication, and feature-store buttons remain placeholders.

## Intended dependency direction

The app UI should call VoidCraft-owned service interfaces. Domain models should not depend on Android Activities or upstream UI classes. Platform-specific file, network, authentication, and runtime adapters should sit behind narrow interfaces so they can be tested and audited independently.

Planned boundaries:
- `core`: stable models and validation.
- `storage`: app-private metadata, safe file operations, backups, and explicit migration adapters.
- `versions`: metadata, dependency and installed-file verification.
- `downloads`: bounded, cancellable queue with retry, integrity checks, and later resume support.
- `accounts`: supported provider sign-in and secure credential storage.
- `runtime`: launch request/result contract; retained Java/native components remain audited dependencies until a viable replacement is proven.
- `features/store`: validated feature manifests, explicit permissions, compatibility checks, rollback, and dedicated feature entry points.

## Data safety invariants

1. Listing an instance never creates, moves, renames, or deletes a game directory.
2. Removing a catalog record never deletes instance files or worlds.
3. Unknown schema versions and malformed catalogs fail visibly rather than being overwritten as an empty catalog.
4. Version-detail and asset-index JSON are checked against metadata SHA-1 when supplied.
5. Every planned path is relative and rejects traversal segments; duplicate destinations must agree on URL/hash/size.
6. Downloads are staged to a temporary file, and supplied checksums/sizes are checked before replacing an existing destination.
7. Any future import, migration, or destructive cleanup must include backup, preview/confirmation, rollback, and tests.
8. The existing launcher and its current build path remain available until parity gates are passed.

## Metadata formats and rule handling

The version browser follows the public Mojang version manifest/version-detail JSON formats. Library rules are evaluated in order: no rules means the library is allowed, otherwise the default is denied and the last matching rule wins. OS name/version/architecture and declared feature flags are part of the platform context passed into the planner. This follows the published launcher metadata behavior described in the [Minecraft Wiki version manifest overview](https://minecraft.wiki/w/Version_manifest) and the rule structure documented in [voxelum's launcher metadata types](https://github.com/voxelum/minecraft-launcher-core-node/blob/master/packages/core/version.ts).

The planner is only a deterministic plan. It does not itself download files, extract natives, choose an Android-compatible renderer, or launch Minecraft. Android-specific runtime integration must supply and verify its platform context before a plan may be acted upon.
