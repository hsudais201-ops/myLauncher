# MyLauncher — Android Edition

An Android-first launcher project for Minecraft: Java Edition, with a mobile UI inspired by modern game launchers.

## Project status

- `index.html` is a browser-based UI preview.
- `android/` is the first native Android app scaffold, built with Kotlin and Jetpack Compose.
- The native app includes a home screen, section navigation, and a local offline player-name setting saved on-device. The Start Game action displays a notice; it does **not** launch Minecraft yet.
- The existing repository files outside `mobile-launcher/` have not been removed.

## Open the native app

1. Install Android Studio and its Android SDK.
2. Open the `mobile-launcher/android` folder in Android Studio.
3. Allow Gradle to sync, then run the `app` configuration on an Android device or emulator.

The project uses JDK 17, Android Gradle Plugin 8.10.1, Kotlin 2.1.20, compile SDK 35, and minimum Android API 26. A build has not yet been verified in CI.

## Roadmap

1. Native mobile launcher UI and navigation. (Initial scaffold added.)
2. Local offline profile creation and profile management. (Basic saved player name added.)
3. Minecraft version metadata, downloads, assets, libraries, and integrity verification.
4. Java runtime integration and correct JVM/classpath argument assembly.
5. Microsoft sign-in using the genuine approved OAuth/Xbox/Minecraft authentication flow.
6. Optional feature store with explicit install/uninstall and integrity checks.
7. Touch controls, renderer integration, and device compatibility testing.

## Account modes

- **Offline profile:** a local display name for testing and supported offline use. It does not authenticate a player for online-mode servers.
- **Microsoft account:** must use a real, approved Microsoft/Xbox/Minecraft authentication flow. Never simulate successful authentication or collect passwords directly.

## Security

- Verify downloads against trusted hashes when available.
- Never execute arbitrary downloaded feature code.
- Keep access tokens out of logs and source control.
- Request only the Android permissions that are needed.
