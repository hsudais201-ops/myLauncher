# VoidCraft Cosmetics Fabric client mod

- Target: Minecraft Java 1.21.1, Fabric Loader, Fabric API, Java 21.
- Renders the equipped cosmetic as a small orbiting particle aura around the local player using Minecraft's built-in particle renderer.
- Config: `.minecraft/config/voidcraft-cosmetics.json`, format `{"equippedId":"void-aura"}`.
- Supported IDs: `void-aura`, `neon-mint`, `arctic-ice`, `solar-ember`. Missing or invalid IDs disable the effect.
- Client-side only. Other players will not see it unless they run a compatible mod or a compatible server/client system distributes and renders the cosmetic.
- This implements particle effects, not arbitrary 3D accessories or custom capes.
