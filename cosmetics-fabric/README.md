# VoidCraft Cosmetics — Fabric client mod

Initial target: Minecraft Java Edition **1.21.1**, Fabric Loader **0.16.10+**, Fabric API **0.115.6+1.21.1**, Java 21.

The client mod registers a player feature renderer and draws a colored, four-sided halo around the local and other player models on clients that have this mod installed. The equipped cosmetic is read from `config/voidcraft-cosmetics.json` in the Minecraft game directory.

Example configuration:

```json
{"equipped":"void-aura"}
```

Supported IDs: `void-aura`, `neon-mint`, `arctic-ice`, `solar-ember`. The VoidCraft Android launcher writes this config through the integrated Amethyst launch activity when the cosmetic ID is passed in the launch intent.

## Build

From this directory, with JDK 21 installed:

```sh
gradle build
```

The mod JAR is created under `build/libs/`. Install it into the selected Minecraft instance's `mods` directory along with Fabric API. For the first iteration, Minecraft 1.21.1 Fabric is the supported target; other versions are not claimed compatible.

## Multiplayer limits

The effect is client-rendered. Other players see it only if their client also has a compatible VoidCraft Cosmetics mod and their rendering setup supports it. This mod does not add server-side cosmetics, upload skins/capes, or make vanilla clients render the effect.
