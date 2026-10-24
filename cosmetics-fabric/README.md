# VoidCraft Cosmetics — version-specific Fabric client mod

VoidCraft cosmetics use a **version-specific client mod**, because Minecraft changes rendering APIs between releases. There is no safe universal JAR for every Minecraft version.

The equip handoff writes `config/voidcraft-cosmetics.json` in the active Minecraft game directory. The file includes a schema version, selected cosmetic ID, and selected Minecraft version:

```json
{"schemaVersion":1,"equipped":"void-aura","minecraftVersion":"1.21.1"}
```

Cosmetic IDs: `void-aura`, `neon-mint`, `arctic-ice`, `solar-ember`. The renderer currently draws a four-sided halo around player heads.

## Version builds

The CI workflow compiles an exact JAR for each target listed in its version matrix. It records the exact Minecraft version in the JAR metadata and artifact name. Install the JAR built for the selected Minecraft version, the matching Fabric Loader, and the Fabric API build for that same version. Do not copy one target JAR to a different game version merely because the game version number looks similar.

The initial matrix covers selected stable release targets from Minecraft 1.17.1 through 1.21.7. This is a compatibility expansion, not a claim that every Minecraft release is covered yet: snapshots, versions before 1.17.1, and the newer rendering architecture starting in later 1.21 releases need separate adapters and successful builds before they can be marked supported. The launcher handoff is version-aware, but automatic mod/Fabric API installation is not implemented yet.

## Build locally

Use JDK 21 and Gradle 8.10.2 from this directory:

```sh
gradle clean test build --no-daemon
```

For a different target, override the game version, mappings, Fabric API, Java metadata, and bytecode target using the values declared in the CI matrix. Every target build must pass before its artifact can be treated as supported.

## Multiplayer limits

The effect is client-rendered. Other players see it only when their clients have a compatible VoidCraft Cosmetics mod and compatible rendering setup. This mod does not add server-side cosmetics, upload skins/capes, or make vanilla clients render the effect.
