# VoidCraft Cosmetics (Fabric)

## What is implemented

- The launcher's Cosmetic Store writes the equipped cosmetic ID to `config/voidcraft-cosmetics.json` inside the selected Minecraft profile directory.
- The Fabric client mod reads that setting and renders the selected cosmetic locally.
- Supported cosmetic IDs: `void-aura`, `neon-mint`, `arctic-ice`, and `solar-ember`. Choosing **Unequip cosmetic** writes `none`.
- The mod is built and checked separately for these Minecraft releases: 1.17.1, 1.18.2, 1.19.4, 1.20.1–1.20.6, 1.21–1.21.11 (only the explicitly listed release targets; this does not mean every snapshot or patch is supported).

## Install the matching client mod

1. Open the [VoidCraft Fabric cosmetics workflow](https://github.com/hsudais201-ops/myLauncher/actions/workflows/cosmetics-fabric.yml).
2. Open a successful run for the `feature/cosmetic-store-fabric-handoff` branch and download the artifact whose name exactly matches your Minecraft version, for example `voidcraft-fabric-cosmetics-mc-1.21.7`.
3. Extract the downloaded ZIP. It contains the mod JAR and a SHA-256 checksum text file. Keep the JAR for your exact Minecraft version; do not rename a JAR from another version to make it appear compatible.
4. Install Fabric Loader for that Minecraft version and add the compatible Fabric API to the same profile if your setup requires it.
5. Put the JAR in that profile's `mods` folder, then launch the game once with Fabric.
6. Open VoidCraft's Cosmetic Store, select a style, and launch that same profile.

The launcher currently saves the equip choice but does **not** automatically download or install the Fabric mod. A selected style alone does not make the renderer available.

## Multiplayer visibility

Rendering is client-side. Other players will not automatically see the cosmetic. They need a compatible client-side renderer and matching cosmetic configuration/system; this project does not claim server-authoritative or universal multiplayer visibility.

## Troubleshooting

- **No effect:** verify Fabric Loader is active, the mod JAR matches the exact Minecraft version, the JAR is in the active profile's `mods` folder, and the selected profile's `config/voidcraft-cosmetics.json` contains the intended ID.
- **Wrong profile:** open Cosmetic Store from the launcher's main menu after selecting the profile you intend to launch.
- **Version missing:** only the exact versions built by the workflow are supported. Snapshots and unlisted versions are not covered by these checks.
- **Verify a downloaded JAR:** compare its SHA-256 hash with the checksum file included in the same workflow artifact.
