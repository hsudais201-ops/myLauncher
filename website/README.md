# VoidCraft official website

Static, mobile-first website for VoidCraft Launcher. Pages include home, downloads, Feature Store, Cosmetic Store, server status, community, developer hub, documentation, security, changelog, profile, sign-in, privacy, terms, and 404.

Cloudflare Pages project: `voidcraft-launcher` (`https://voidcraft-launcher.pages.dev`). Website source is kept in `website/`, separate from launcher source and runtime files.

## Website account system

- Registration and sign-in use a Cloudflare Pages Functions API backed by the Cloudflare D1 database `voidcraft-auth`.
- Passwords are stored as salted, chained PBKDF2-HMAC-SHA-256 hashes using six sequential 100,000-iteration passes to stay within the Cloudflare Workers per-call limit; raw passwords are not stored.
- Session tokens are random, stored server-side as SHA-256 hashes, and sent in a Secure, HttpOnly, SameSite cookie that expires after seven days.
- Registration and sign-in are rate limited per request IP. State-changing auth endpoints reject cross-origin requests.
- The account system is for the VoidCraft website only. It is not Microsoft/Xbox/Minecraft authentication and grants no Minecraft entitlement.
- Email verification, email delivery, password recovery, self-service deletion, purchases, user uploads, live server ping, feature package installation, and launcher-linked actions are not yet enabled. Do not claim those are available.
- Before broad public registration, complete live tests for register/login/logout, rate limits, session expiry, cookie flags, and recovery/security processes.

## Image previews

Original local SVG concept illustrations appear on the homepage, Feature Store, and Cosmetic Store. Click any preview to open the accessible enlarged image viewer. Store imagery is illustrative and does not mean a feature is implemented or installable.
