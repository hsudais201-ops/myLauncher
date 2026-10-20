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
- Parse client-download/asset-index details.
- Reject detail-ID mismatch and version-detail JSON whose SHA-1 does not match the catalog entry.

**Downloader**
- Verify SHA-1/SHA-256 and expected size.
- Preserve an existing destination on checksum failure, cancellation, or oversized response.
- Retry a transient HTTP server error and then succeed.
- Reject non-HTTPS URLs before opening a connection.
- Reject an HTTPS-to-HTTP redirect.

The version-browser branch passed its unit-test and debug APK build steps in [GitHub Actions run #39398436918](https://github.com/hsudais201-ops/myLauncher/actions/runs/39398436918). This run predates the new SHA-1 validation/test being added in the next commit; check the current [workflow runs](https://github.com/hsudais201-ops/myLauncher/actions) before claiming that newer check passed.

## Manual/device testing still required

A passing compile or JVM unit-test run does not prove that the app was installed or tested on an Android device. Device acceptance remains open for:
- Install and cold start on Android 9+.
- Rotation, background/resume, process recreation, accessibility and small-screen layout.
- Manual network tests against the official metadata service with bounded timeouts, redirects, offline conditions and HTTP failures.
- Storage inspection confirming the catalog and staged downloads stay within the intended app-private directory.
- Large-file/disk-full tests and destination replacement behavior on Android filesystems.
- Actual install orchestration, authentication, renderer, touch input, and Minecraft launch after those components are implemented.

## Release gate

Do not describe this shell as a Minecraft-launching replacement. Do not remove the existing Amethyst-based build or migrate user data until the relevant end-to-end, license, and device checks have passed and recorded evidence is available.
