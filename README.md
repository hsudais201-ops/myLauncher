# Hyper Launcher — Mobile UI

A mobile-first interface prototype for a future Android launcher for Minecraft Java Edition.

## Preview

Open [index.html](./index.html) in a mobile browser, or enable GitHub Pages to publish the static preview. The layout is designed for touch screens and includes safe-area spacing for modern phones.

## Current UI features

- Phone-first home screen and fixed bottom navigation.
- Pixel-inspired landscape and featured Minecraft instance card.
- Minecraft version selector and interactive memory allocation control.
- Offline Player profile placeholder.
- Touch-friendly dialogs for planned sections.

## Important status

This is currently a **static UI prototype**, not an installable Android app and not a functioning Minecraft launcher. It cannot download or launch Minecraft yet.

To become a real Android Java Edition launcher, the next phases need to implement:
1. An Android app shell and device/architecture checks.
2. Game-version manifests, asset/library downloads, and integrity checks.
3. A compatible Java runtime for Android and process launching.
4. Microsoft account authentication for licensed accounts.
5. Any offline/local profile mode with clear limits; it must not bypass Minecraft authentication or licensing.
6. Instance storage, memory limits based on the phone, logs, and error handling.

## Files

- `index.html` — mobile-first UI
- `mobile.css` — phone layout, touch controls, and safe-area styling
- `app.js` — preview interactions
- `styles.css` — previous desktop UI styling (not loaded by the mobile entry page)

The `main` branch is preserved.
