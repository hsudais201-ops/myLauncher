# VoidCraft Cosmetics — Fabric client mod

This module contains the client-side halo renderer and shared model for selected Minecraft release targets. The launcher-native **Cosmetic Store** writes the selected ID to the configured game folder at `config/voidcraft-cosmetics.json`.

```json
{
  "schemaVersion": 1,
  "equipped": "void-aura"
}
```

Supported IDs are `void-aura`, `neon-mint`, `arctic-ice`, and `solar-ember`. `none` disables the effect. The renderer validates the ID and safely falls back to no effect for a missing or malformed file.

## Compatibility lanes

The CI matrix builds separate JARs for selected stable releases from **1.17.1 through 1.21.7** (see `.github/workflows/cosmetics-fabric.yml`). This is not every Minecraft version or every patch release. Minecraft **1.21.8–1.21.11** use the separate `cosmetics-fabric-expanded/` lane because of late-1.21 rendering API changes. Snapshots, versions before 1.17.1, and Minecraft 26.x+ are not covered by these adapters.

The artifact name includes the exact Minecraft version. Install the matching Fabric Loader, Fabric API and VoidCraft Cosmetics JAR. A JAR built for a different target is not interchangeable.

## Build locally

For the legacy and state-based lane, use JDK 21 and Gradle 8.12.1:

```sh
gradle clean test build --no-daemon
```

Pass the target's Minecraft, mappings, Fabric API, Fabric Loader, Java dependency and bytecode version as `-P` properties when building a matrix entry. The late 1.21 lane uses JDK 21, Gradle 9.2.0 and its own workflow.

## Current boundaries

The launcher selection screen writes the equip config, but it does not yet automatically download/install the version-specific Fabric mod. The website export is a manual config handoff and does not write game files. This renderer is client-side: other players see the effect only when their clients also have a compatible VoidCraft Cosmetics mod. No vanilla-client or server-side visibility is claimed.
