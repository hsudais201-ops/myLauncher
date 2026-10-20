# VoidCraft UI previews

These are separate, standalone design previews so the Android launcher and public website can be developed independently.

- `android-launcher/index.html` — Android Minecraft launcher concept: home, instance summary, version/loader, Play action, and mobile navigation.
- `website/index.html` — public VoidCraft website concept: landing page, feature overview, download area, feature store, cosmetic store, and community section.

## Important

Both pages are UI prototypes, not production integrations. The sample Minecraft version, Fabric loader, memory, account, and activity labels are illustrative. Buttons that say preview or show an alert do not launch Minecraft, authenticate accounts, process payments, or download an APK.

The website is separate from the Android application UI. No existing engine, runtime, renderer, native library, or user data should be replaced as part of this visual work. Before integrating the Android design, locate and audit the actual Android source module, then wire UI actions to existing launcher services and validate on a device.

The design is inspired by general modern Minecraft-launcher patterns; VoidCraft branding, layout, and CSS are original. Reference product: [Hyper Launcher on Google Play](https://play.google.com/store/apps/details?id=net.ashmeet.hyperlauncher).

## Preview locally

Open either HTML file in a browser. They have no build step and do not require external JavaScript libraries.
