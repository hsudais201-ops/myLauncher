# Testing the independent VoidCraft shell

## Automated checks

The GitHub Actions workflow `.github/workflows/voidcraft-shell.yml` runs:

```sh
cd voidcraft-app
gradle --no-daemon testDebugUnitTest assembleDebug
```

Current JVM test coverage:

- **Instance catalog:** metadata round-trip, duplicate updates, path-like ID rejection, unknown schema handling, and ensuring removal does not delete a simulated world/save file.
- **Version metadata:** manifest/detail parsing, release/snapshot/old IDs, allowed HTTPS hosts, timestamps and hashes, latest references, detail-ID checking, and metadata SHA-1 integrity.
- **Install planning:** asset-index hash/size parsing, content-addressed URLs/paths, ordered allow/disallow rules, OS constraints, native classifiers, extraction excludes, deduplication, and incomplete-plan warnings.
- **Inheritance:** recursive grandparent/parent/child resolution, child metadata overrides, inherited asset/Java/client data, library-coordinate replacement, correct inherited client path, missing parent, cycle detection, and unresolved-parent rejection.
- **Install preparation:** child + parent resolution, asset index SHA-1/size verification, asset object enumeration, inherited client destination planning, warning reporting when client/assets are missing, and rejecting an invalid asset-index hash.
- **Downloads:** supplied SHA-1/SHA-256 and size checks, transient retry, cancellation, unsafe URL/redirect rejection, and destination preservation on failure.
- **Install executor:** verified multi-file staging/promotion, progress, cancellation before commit, incomplete-plan rejection, traversal rejection before network requests, and rollback preserving existing data if commit fails.
- **Native archive extraction:** zip-slip/traversal rejection, metadata extraction exclusions, per-entry and total expanded-size limits, compression-ratio limit, cancellation cleanup, and refusal to overwrite an existing destination. The extractor is a tested component but is not yet connected to a complete runtime-launch pipeline.
- **Launch argument interpolation:** placeholder expansion without splitting paths that contain spaces, platform-rule filtering, missing/invalid placeholder rejection, NUL rejection, stable order, and no recursive expansion. A full launch context, legacy argument compatibility, authentication values, and actual process start are still separate work.

The library/asset planner passed in [Actions run #39413275839](https://github.com/hsudais201-ops/myLauncher/actions/runs/39413275839). Treat new extraction and launch-argument tests as pending until the current branch workflow completes successfully.

## Manual/device testing still required

A passing compile/JVM test run does not prove physical-device compatibility or successful Minecraft launch. Device acceptance remains open for:
- Install/cold start on Android 9+ and small-screen/accessibility behavior.
- Metadata fetch through redirects, network failures, offline mode, and oversized response checks.
- Storage inspection and destination replacement behavior on Android filesystems.
- Insufficient-storage and interrupted multi-artifact tests.
- Platform-rule validation against an actual Android-compatible runtime, including ARM64/ARM32.
- End-to-end integration of native extraction with downloaded native archives and launch setup.
- Full install/repair through the visible app UI, authentication, renderer, touch input, and actual Minecraft launch after those components are implemented.

## Release gate

Do not describe this app as a Minecraft-launching replacement or mark a planned feature complete based on a UI or debug build. Do not remove the existing Amethyst-based build or migrate user data until end-to-end, license, data-preservation, and device checks have passed.
