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
- Parse client-download/asset-index details and reject a version-detail ID mismatch.

**Downloader**
- Verify SHA-1/SHA-256 and expected size.
- Preserve an existing destination on checksum failure, cancellation, or oversized response.
- Retry a transient HTTP server error and then succeed.
- Reject non-HTTPS URLs before opening a connection.
- Reject an HTTPS-to-HTTP redirect.

The profile-only test and APK build previously passed in [run #39359802263](https://github.com/hsudais201-ops/myLauncher/actions/runs/39359802263). That run predates the new metadata/downloader services, so it is not evidence that the newer code has passed. Check the [current workflow runs](https://github.com/hsudais201-ops/myLauncher/actions) for final results.

## Manual/device testing still required

A passing compile or JVM unit-test run does not prove that the app was installed or tested on an Android device. Device acceptance remains open for:
- Install and cold start on Android 9+.
- Rotation, background/resume, process recreation, accessibility and small-screen layout.
- Manual network tests against the official metadata service with bounded timeouts, redirects, offline conditions and HTTP failures.
- Storage inspection confirming the catalog and staged downloads stay within the intended app-private directory.
- Large-file/disk-full tests and destination replacement behavior on Android filesystems.
- Version installation, authentication, renderer, touch input, and actual Minecraft launch after those components are implemented.

## Release gate

Do not describe this shell as a Minecraft-launching replacement. Do not remove the existing Amethyst-based build or migrate user data until the relevant end-to-end, license, and device checks have passed and recorded evidence is available.
