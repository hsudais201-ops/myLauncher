# VoidCraft architecture (migration work in progress)

## Current implementation

The independent app is in `voidcraft-app/`, uses Kotlin and Android platform Views, and has its own Android application ID: `com.voidcraft.launcher`.

Implemented:
- `MainActivity`: independently authored dashboard and navigation; no upstream Activity or UI code is imported.
- `InstanceListActivity`: profile metadata list/create/remove UI, with explicit messaging that game files are not installed and removal is non-destructive.
- `VersionBrowserActivity`: fetches official Mojang version metadata on a background executor, filters/searches the catalog, fetches details on selection, and creates profile metadata only.
- `core/InstanceProfile.kt`: validated metadata model for one game instance.
- `core/InstanceCatalogRepository.kt`: versioned, app-private metadata catalog with temporary-file writes, schema validation, and metadata-only removal.
- `core/MinecraftVersionCatalog.kt`: manifest/detail data models, parser validation, official Mojang manifest client, version-detail SHA-1 verification, HTTPS host/redirect checks, and bounded response reads.
- `core/SecureFileDownloader.kt`: HTTPS file transfer with bounded size, progress callback, cancellation, transient retries, optional hashes, unique temporary-file staging, and validated destination replacement.
- `core/InstanceCatalogRepositoryTest.kt`, `core/MinecraftVersionCatalogTest.kt`, `core/SecureFileDownloaderTest.kt`: JVM tests for profile safety, metadata/policy and SHA-1 checks, download retries/cancellation, and artifact integrity.

Not yet implemented or connected:
- A complete version-install orchestration flow to resolve libraries/assets and merge inherited version metadata.
- Resumable downloads and a persistent download queue.
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
- `versions`: metadata and installed-file verification.
- `downloads`: bounded, cancellable queue with retry, integrity checks, and later resume support.
- `accounts`: supported provider sign-in and secure credential storage.
- `runtime`: launch request/result contract; retained Java/native components remain audited dependencies until a viable replacement is proven.
- `features/store`: validated feature manifests, explicit permissions, compatibility checks, rollback, and dedicated feature entry points.

## Data safety invariants

1. Listing an instance never creates, moves, renames, or deletes a game directory.
2. Removing a catalog record never deletes instance files or worlds.
3. Unknown schema versions and malformed catalogs fail visibly rather than being overwritten as an empty catalog.
4. Version-detail JSON is checked against the SHA-1 provided by the trusted manifest before parsing.
5. Downloads are staged to a temporary file, and supplied checksums/sizes are checked before replacing an existing destination.
6. Any future import, migration, or destructive cleanup must include backup, preview/confirmation, rollback, and tests.
7. The existing launcher and its current build path remain available until parity gates are passed.
