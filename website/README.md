# VoidCraft official website

Static, mobile-first website for VoidCraft Launcher. Pages include home, downloads, Feature Store, Cosmetic Store, server status, community, developer hub, documentation, security, changelog, profile, sign-in, privacy, terms, and 404.

Cloudflare Pages project: `voidcraft-launcher` (`https://voidcraft-launcher.pages.dev`). Website source is kept in `website/`, separate from launcher source and runtime files.

## Website account system

- Registration and sign-in use a Cloudflare Pages Functions API backed by the Cloudflare D1 database `voidcraft-auth`.
- Passwords are stored as salted, chained PBKDF2-HMAC-SHA-256 hashes using six sequential 100,000-iteration passes to stay within the Cloudflare Workers per-call limit; raw passwords are not stored.
- Session tokens are random, stored server-side as SHA-256 hashes, and sent in a Secure, HttpOnly, SameSite cookie that expires after seven days.
- Registration and sign-in are rate limited per request IP. State-changing auth endpoints reject cross-origin requests.
- The account system is for the VoidCraft website only. It is not Microsoft/Xbox/Minecraft authentication and grants no Minecraft entitlement.
- The Feature Store and Cosmetic Store use the D1-backed `store_items` catalog. Seeded listings are explicitly marked as previews and do not imply a working package.
- Developer submissions use the authenticated `/api/developer/submissions` routes; submissions remain private pending owner review. Only users with `role = 'owner'` can publish or reject pending listings through `/api/developer/review`.
- Free packages are intended to be direct HTTPS downloads and must be explicitly linked to a published, available catalog item. Sign-in is required for paid purchase attempts, not free downloads.
- Payment checkout, purchase entitlements, direct binary uploads/R2 storage, automated package scanning/signing, email verification/delivery, password recovery, self-service deletion, live server ping, and launcher-linked installation are not yet configured. Paid checkout intentionally returns a safe unavailable response and never reports a fake purchase. Do not claim these capabilities are active.
- Before broad public registration, complete live tests for register/login/logout, rate limits, session expiry, cookie flags, and recovery/security processes.

## Image previews

Original local SVG concept illustrations appear on the homepage, Feature Store, and Cosmetic Store. Click any preview to open the accessible enlarged image viewer. Store imagery is illustrative and does not mean a feature is implemented or installable.

## Current homepage and roles

- The homepage documents the launcher foundation, Android-first goals, version/instance workflows, mods/loaders, touch controls, safety/performance goals, optional Feature Store ideas, cosmetic concepts, community resources, and which areas remain planned or unconnected. Use it as a feature overview, not as a guarantee that every roadmap item ships in the current launcher build.
- Website usernames are selected at registration together with the email and password. New accounts receive the `member` role by default; owner status is stored in the D1 `users.role` column and exposed through the authenticated profile API. Never grant elevated roles from a username submitted by an unauthenticated visitor.
- The global header checks the website session. When signed in, it hides the Sign in link and shows the username/profile link; the owner account displays a crown based on the server-stored `owner` role. Website owner status applies only to the website and does not grant GitHub, Cloudflare, Microsoft, Xbox, Minecraft, or launcher-engine permissions.

## Online marketplace

- Apply `migrations/0003_store_marketplace.sql` after the authentication and role migrations for new databases. It creates the catalog schema and inserts the existing concept previews idempotently.
- `GET /api/store/catalog?kind=feature` and `GET /api/store/catalog?kind=cosmetic` expose published listings only.
- `POST /api/developer/submissions` requires an authenticated website account and accepts listing metadata plus HTTPS source/package links. It does not upload package bytes.
- `GET /api/developer/submissions` returns the signed-in user's submissions; an owner can view the moderation queue.
- `POST /api/developer/review` is owner-only and approves or rejects pending submissions. Publishing a link is a moderation decision, not proof of malware scanning or runtime compatibility.
- `POST /api/store/purchase` validates that the listing is published and paid, but responds that checkout is unavailable until a payment provider and entitlement delivery are configured. Never accept client-side claims of payment.

## Database migration for account roles

For a separate or newly created authentication database, apply `migrations/0002_account_roles.sql` exactly once after the original authentication tables are present. All existing and newly registered accounts default to `member`. Assign the `owner` role only through a trusted administrative operation; never accept an owner role from public registration form input.
