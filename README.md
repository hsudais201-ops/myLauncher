# Hyper Launcher — Mobile UI

A mobile-first interface prototype for a future Android launcher for Minecraft Java Edition, inspired by the clean, game-focused Hyper Launcher look.

## Preview

Open [index.html](./index.html) in a mobile browser, or enable GitHub Pages to publish the static preview. The layout is designed for touch screens and includes safe-area spacing.

## Working preview features

- Phone-first home screen and fixed bottom navigation.
- Pixel-inspired landscape and featured Minecraft instance card.
- Version selector with an updated version summary.
- Memory target slider with a live value.
- Version and memory preferences saved in browser local storage when available.
- Offline Player profile placeholder and clear prototype-status dialogs.

## Important status

This is a **static browser UI prototype**, not an installable Android app and not a functioning Minecraft launcher. The Play button does not start Minecraft. Browser storage saves only the selected version and memory target; it does not install or run game files.

To become a real Android Java Edition launcher, the next phases need to implement:

1. An Android app shell and device/architecture checks.
2. Game-version manifests, asset/library downloads, and integrity checks.
3. A compatible Java runtime for Android, complete classpath assembly, and process launching.
4. Microsoft account authentication for licensed accounts.
5. Any offline/local profile mode with clear limits; it must not bypass Minecraft authentication or licensing.
6. Instance storage, device-aware memory limits, logs, and error handling.

## Files

- `index.html` — mobile-first UI
- `mobile.css` — phone layout, touch controls, and safe-area styling
- `app.js` — preview interactions and local preference persistence
- `styles.css` — previous desktop UI styling (not loaded by the mobile entry page)

The `main` branch is preserved.
