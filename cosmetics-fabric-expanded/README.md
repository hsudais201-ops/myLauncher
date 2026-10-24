# VoidCraft Cosmetics — late 1.21.x Fabric compatibility lane

This project reuses the halo model, manifest and tests from `../cosmetics-fabric/`, and compiles a separate renderer adapter for Minecraft 1.21.8, 1.21.9, 1.21.10 and 1.21.11. Rendering APIs changed within late 1.21.x, so these JARs are built and named per exact game version.

Targets use JDK 21, Gradle 9.2.0 and Fabric Loom 1.14.9. CI runs unit tests, checks JAR integrity and verifies that `fabric.mod.json` declares the matching Minecraft version. The launcher writes the selected cosmetic ID into `config/voidcraft-cosmetics.json`; the mod reads this file at client startup.

This lane does not cover snapshots or Minecraft 26.x+. Newer unobfuscated Minecraft versions need a separate adapter/toolchain and a passing build before support can be claimed. The halo is client-rendered; other players see it only with a compatible mod on their client.
