# Testing the independent VoidCraft shell

## Automated checks

The GitHub Actions workflow `.github/workflows/voidcraft-shell.yml` runs:

```sh
cd voidcraft-app
gradle --no-daemon testDebugUnitTest assembleDebug
```

Current unit-test coverage targets the instance catalog:
- Save and reload profile metadata.
- Replace an existing profile ID without duplicates.
- Reject unsafe path-like IDs.
- Fail closed on an unknown schema version.
- Ensure catalog removal does not delete a simulated world/save file.

## Manual/device testing still required

A passing compile or JVM unit-test run does not prove that the app was installed or tested on a physical Android device. Device acceptance remains open for:
- Install and cold start on Android 9+.
- Rotation, background/resume, and process recreation.
- Text scaling/accessibility and small-screen layout.
- Storage inspection confirming the app-private catalog location.
- Runtime connection, authentication, downloads, renderer, touch input, and actual Minecraft launch after those components are implemented.

## Release gate

Do not describe this shell as a Minecraft-launching replacement. Do not remove the existing Amethyst-based build or migrate user data until the relevant end-to-end and device checks have passed and recorded evidence is available.
