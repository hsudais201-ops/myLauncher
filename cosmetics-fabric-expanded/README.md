# VoidCraft Cosmetics — late 1.21.x compatibility lane

This Gradle project reuses the client renderer and tests from `../cosmetics-fabric/` and builds separate mod JARs for Minecraft 1.21.8, 1.21.9, 1.21.10, and 1.21.11.

These releases are deliberately built in a separate lane from 1.17.1–1.21.7: the Fabric toolchain and mappings changed during late 1.21.x. The test workflow only marks a target as build-supported after compilation, unit tests, JAR integrity, and exact metadata checks pass.

Targets are built with JDK 21, Gradle 8.14.3, and Fabric Loom 1.14.9. Each artifact is version-specific; install the matching JAR, Fabric Loader, and Fabric API for that Minecraft version.

This is not the 26.x compatibility lane. Minecraft 26.1+ is unobfuscated and uses the newer Loom plugin ID and different Minecraft source names, so it needs a separate adapter and build configuration.

The renderer is client-side. Other players only see the halo if their own clients have a compatible mod. Build success does not replace an in-game visual test.
