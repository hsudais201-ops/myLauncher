# Testing the independent VoidCraft shell

## Automated checks

The GitHub Actions workflow `.github/workflows/voidcraft-shell.yml` runs:

```sh
cd voidcraft-app
gradle --no-daemon testDebugUnitTest assembleDebug
```

Current JVM unit-test coverage:

**Instance catalog**
- Save and reload metadata.
- Replace an existing ID instead of duplicating it.
- Reject path-like IDs.
- Fail closed on an unknown schema version.
- Ensure catalog removal does not delete a simulated world/save file.

**Version metadata**
- Parse release/snapshot entries and latest pointers.
- Accept older version identifiers.
- Reject duplicate IDs, invalid timestamps/hashes, unapproved hosts, HTTP URLs, invalid JSON, and dangling latest pointers.
- Reject version-detail ID mismatch and unexpected metadata SHA-1.

**Install planning**
- Parse the asset-index object map, validate object hashes/sizes, and derive content-addressed URLs/paths.
- Apply ordered allow/disallow rules with OS version constraints.
- Select native classifiers for an explicit OS/architecture context and preserve extraction exclusions.
- Generate deduplicated version/client/library/native/asset download tasks.
- Reject unsafe Maven/artifact paths and produce warnings when inherited metadata or asset lists are unresolved.

**Downloader**
- Verify SHA-1/SHA-256 and expected size.
- Preserve an existing destination on checksum failure, cancellation, or oversized response.
- Retry a transient HTTP server error and then succeed.
- Reject non-HTTPS URLs before opening a connection.
- Reject an HTTPS-to-HTTP redirect.

The previous commit passed tests and debug APK build in [Actions run #39399012705](https://github.com/hsudais201-ops/myLauncher/actions/runs/39399012705); the current install-planner changes require their own fresh result.

## Manual/device testing still required

A passing compile or JVM unit-test run does not prove that the app was installed or tested on an Android device. Device acceptance remains open for:
- Install and cold start on Android 9+.
- Rotation, background/resume, process recreation, accessibility and small-screen layout.
- Manual network tests against official metadata with bounded timeouts, redirects, offline conditions and HTTP failures.
- Storage inspection confirming the catalog and staged downloads stay within the intended app-private directory.
- Disk-full tests and destination replacement behavior on Android filesystems.
- Verifying planned classifiers against the chosen Android runtime, including ARM64/ARM32, before downloads are executed.
- Full installation/repair, authentication, renderer, touch input, and Minecraft launch after those components are implemented.

## Release gate

Do not describe this shell as a Minecraft-launching replacement. Do not remove the existing Amethyst-based build or migrate user data until the relevant end-to-end, license, and device checks have passed and recorded evidence is available.
