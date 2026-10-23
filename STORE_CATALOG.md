# Voidcraft Feature Store

The Android launcher loads the bundled catalog from `app/src/main/assets/store/catalog.json` and tries to refresh it from:

`https://raw.githubusercontent.com/hsudais201-ops/myLauncher/main/store/catalog.json`

Only entries explicitly listed in the `items` array are shown. No sample or legacy entries are added automatically. Optional features remain uninstalled until the user explicitly selects **Install**. The catalog supports built-in components and reviewed HTTPS package listings; downloadable packages are stored in private app storage and are not dynamically loaded as Android code.

## Current catalog entries

- **Social Hub:** local contacts/notes and a staging online beta for beta account sign-in, friend requests and direct messages. Online chat uses manual refresh; there is no email verification, password recovery, push notification or end-to-end encryption.
- **Advanced Mod Manager:** local JAR library with validation and file operations. Mod toggles do not yet load mods into a running Minecraft profile.
- **Server State:** local list of Java/Bedrock server addresses and on-demand status checks using the public mcsrvstat.us service. Each status check sends the selected address to that provider.
- **Theme Studio:** persist a custom launcher accent and presets on the device.
- **Cosmetic Store:** local launcher-profile effects with equip/unequip controls. These do not change Minecraft skins, game files or multiplayer appearance.

## Publishing a listing

Add an object to the `items` array. Required fields: `id`, `name`, `kind`, `category`, and `availability`. Recommended fields include `tagline`, `description`, `version`, `compatibility`, `license`, and `packageUrl`.

- Use a stable unique `id` containing only letters, numbers, dots, underscores or hyphens.
- `kind` should be `launcher` or `content`.
- `availability` should be `available`, `preview` or `coming_soon`.
- Use `builtIn: true` only when the feature is actually implemented in the launcher.
- Publish only HTTPS package URLs that are licensed for redistribution. Never publish access tokens or private URLs.
- Every downloadable `packageUrl` must include a `sha256` field containing the exact 64-character hexadecimal SHA-256 digest of the downloaded bytes. The launcher rejects packages whose digest does not match.
- A non-built-in listing without both a package URL and its SHA-256 digest remains a preview and cannot be installed.
- Downloadable packages are saved in private app storage only. They are not executed as code or automatically loaded by Minecraft.
- After updating the catalog on `main`, users can refresh the Store to load the new catalog. The app tries `main` first and the active development branch as a fallback; if both requests fail, the last bundled catalog remains available.

## Runtime integration still needed

A complete game-content installer must verify package hashes/signatures, reject unsafe archive paths, check version compatibility and write only into the intended Minecraft profile folders. The launcher still needs a real Java runtime/bootstrapper, asset/library downloading, secure Microsoft authentication, profile-aware version installation and an actual game launch path before stored mods, resource packs, shaders, worlds or skins can be applied in-game.
