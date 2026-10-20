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
- **Downloads:** supplied SHA-1/SHA-256 and size checks, transient retry, cancellation, unsafe URL/redirect rejection, and destination preservation on failure.
- **Install executor:** verified multi-file staging/promotion, progress, cancellation before commit, incomplete-plan rejection, traversal rejection before network requests, and rollback preserving existing data if commit fails.

The library/asset planner passed in [Actions run #39413275839](https://github.com/hsudais201-ops/myLauncher/actions/runs/39413275839). The executor added by the current commit must have its own passing CI result before its tests are considered verified.

## Manual/device testing still required

A passing compile/JVM test run does not prove physical-device compatibility or successful Minecraft launch. Device acceptance remains open for:
- Install/cold start on Android 9+ and small-screen/accessibility behavior.
- Metadata fetch through redirects, network failures, offline mode, and oversized response checks.
- Storage inspection and destination replacement behavior on Android filesystems.
- Insufficient-storage and interrupted multi-artifact tests.
- Platform-rule validation against an actual Android-compatible runtime, including ARM64/ARM32.
- Native extraction, full install/repair, authentication, renderer, touch input, and actual Minecraft launch after those components are implemented.

## Release gate

Do not describe this app as a Minecraft-launching replacement or mark a planned feature complete based on a UI or debug build. Do not remove the existing Amethyst-based build or migrate user data until end-to-end, license, data-preservation, and device checks have passed.
