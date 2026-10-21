# Feature Store catalog

The launcher reads the public catalog from:

`https://raw.githubusercontent.com/hsudais201-ops/myLauncher/main/store/catalog.json`

Only items explicitly listed in the `items` array are shown in the Store. No sample, legacy, or placeholder entries are automatically added. Removing an entry from the catalog removes it from the visible catalog after a successful refresh.

## Publishing a listing

Add an object to the `items` array. Required fields: `id`, `name`, `kind`, `category`, and `availability`. Recommended fields include `tagline`, `description`, `version`, `compatibility`, `license`, and `packageUrl`.

- Use a stable, unique `id` containing only letters, numbers, dots, underscores, or hyphens.
- `kind` should be `launcher` or `content`.
- `availability` should be `available`, `preview`, or `coming_soon`.
- Use `builtIn: true` only for a feature actually implemented in the launcher. A built-in feature does not require a package URL.
- A downloadable package must use an HTTPS URL and be licensed for redistribution. Do not publish access tokens or private URLs.
- A non-built-in listing without a package URL stays a preview and cannot be installed.
- Catalog updates appear after the catalog is merged to `main` and the user refreshes the Store or opens it while online.
- If the catalog is intentionally empty, the Store shows “No features added yet.”

## Current catalog item: Social Hub

Social Hub is an optional built-in feature with two modes. Local contacts and notes stay on the device. The online beta uses the staging backend documented in `backend/social-hub/README.md` and supports username/password beta accounts, friend requests, accepting requests, and direct messages between accepted friends. Online messages use manual refresh/polling in this first UI integration; push notifications and WebSockets are not included. The staging service does not yet have email verification or password recovery, so use only a unique test password and do not reuse credentials. This is not ready for production launch.

## Other limitations

The Store interface and catalog refresh are implemented in the bootstrap. Generic external packages can be downloaded to Voidcraft's private app storage; they are **not** installed into Minecraft profiles yet. Dynamic native Kotlin/Android code loading is not supported by this catalog design. A real game-content installer must validate package hashes/signatures, compatibility, safe archive paths, and destination folders before enabling Mods, Resource Packs, Shaders, or Worlds as installable.
