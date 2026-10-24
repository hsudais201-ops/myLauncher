# Third-party notices for the standalone VoidCraft shell

This list covers dependencies explicitly added to `voidcraft-app/` so far. It is not the final launcher APK bill of materials.

## Runtime dependency

- **Gson 2.11.0** — JSON parser used by the independently authored Minecraft metadata readers.
  - Coordinate: `com.google.code.gson:gson:2.11.0`
  - License reported by the project's 2.11.0 Maven metadata: Apache License 2.0.
  - License/source: https://github.com/google/gson/tree/gson-parent-2.11.0
  - License text: https://github.com/google/gson/blob/gson-parent-2.11.0/LICENSE

## Test dependency

- **JUnit 4.13.2** — unit tests only via `testImplementation`; it is not intentionally included in the app runtime.
  - Coordinate: `junit:junit:4.13.2`
  - Verify its exact artifact license and transitive test dependencies in the generated dependency report before distributing test artifacts as a source bundle.

## Build tooling and Android platform

The app also uses the Android Gradle Plugin, Kotlin Gradle plugin, Gradle, Android SDK platform/build tools, and their transitive build-time dependencies. Pin and inventory the actual resolved versions when producing the release SBOM.

## Important scope boundary

This notice does not cover the existing `main` build, which fetches and builds the pinned Amethyst-Android project and its submodules/dependencies. See the repository-level `VOIDCRAFT_SOURCE_AND_LICENSE_AUDIT.md` for the preliminary Amethyst/dependency review. The whole current launcher cannot be called copyright-free on the basis of this standalone module's dependencies.
