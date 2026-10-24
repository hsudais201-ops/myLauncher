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
- Stripe Checkout and server-confirmed purchase entitlements are implemented through `/api/store/checkout`, `/api/store/confirm`, `/api/store/entitlements` and the signature-verified `/api/store/stripe-webhook`. Paid purchases remain unavailable until production secrets `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET` are configured, the webhook is registered, and listings have real prices plus released packages. Without that setup, checkout stays unavailable and no charge or entitlement is fabricated. Direct package uploads/R2 storage, automated package scanning/signing, email verification, password-reset email delivery, self-service deletion, live server ping and launcher-linked install are not fully configured.
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


## Password reset and checkout configuration

- `forgot-password.html` provides the email → six-digit code → new password flow. Codes expire after 10 minutes, are HMAC-hashed with a server secret, are limited to five verification attempts, and are invalidated after use. All previous sessions are revoked after a successful reset.
- The live account database includes `password_reset_codes`. Apply `migrations/0004_password_reset_codes.sql` to a new database after the authentication and role migrations.
- Password reset email delivery requires a Cloudflare Email Sending domain that is onboarded and DNS-verified, plus Pages production secrets: `CF_EMAIL_API_TOKEN` (token with permission to send email for the account), `CF_EMAIL_FROM` (sender address on the verified domain), and `PASSWORD_RESET_SECRET` (a random secret of at least 32 characters). `CF_ACCOUNT_ID` is optional; the current VoidCraft account is the default. Without these settings, requests fail clearly and no code is fabricated or displayed.
- The Cloudflare account connected during implementation currently has no Cloudflare-managed zones/custom domain listed, so email delivery cannot be enabled until a domain is added and verified. Do not treat password recovery as operational until a real inbox receives and verifies the code.
- A cart shortcut appears in the site header. The cart stores item IDs and quantities in browser storage. Prices, catalog state, package links and purchase eligibility are re-read server-side at checkout. Direct Buy Now goes straight to checkout; Add to Cart persists the item across page visits. Preview-only listings have purchase actions disabled.
- `POST /api/store/checkout` validates the authenticated session, server-owned prices, package availability and quantities, then creates a Stripe Checkout session only when the Stripe secrets and release configuration are present. `/api/store/confirm` and the signature-verified webhook verify payment before granting entitlements. Until secrets, webhook and a released paid listing are configured in production, the page should keep Buy now disabled or return a clear unavailable response; it must never claim a fake purchase.
- The public catalog exposes only a boolean `packageAvailable` for paid listings; it does not return paid package URLs. A future delivery endpoint must check a confirmed payment entitlement before returning a signed/private download URL.


## Social Hub browser beta

- `social-hub.html` connects to `https://voidcraft-social-hub-staging.hsudais201.workers.dev` for separate beta account registration/sign-in, friend requests, direct messaging, blocking/unblocking and reports.
- Social Hub login is separate from the website account and is not Microsoft/Xbox/Minecraft sign-in. The browser stores its bearer session only in `sessionStorage` for the current tab.
- This is a staging beta: email verification, password recovery, push notifications and end-to-end encryption are not implemented. Do not reuse passwords or share sensitive information. Live browser and account-abuse/security testing is required before any production launch.


## Orbit website refresh — 24 October 2026

- The public-facing pages now share the Orbit mobile-first design system in `assets/styles.css`: responsive header, accessible mobile/tablet navigation, consistent cards and actions, comfortable touch targets, focus styling and reduced-motion support.
- `assets/app.js` provides the shared navigation toggle, current-page indicator, skip link, mobile Chat & Friends shortcut and VoidCraft logo treatment for sign-in views.
- The homepage, website sign-in, documentation, community, downloads and changelog pages were rebuilt around the common design. Feature Store, Cosmetic Market, server tools, Theme Studio, Mod Manager, cart, checkout, profile, recovery and Social Hub routes were retained rather than replaced with nonfunctional mockups.
- Website sign-in and Social Hub use matching VoidCraft branding but remain separate authentication systems until a secure backend integration is deliberately implemented. Never tell users the accounts are linked.
- A website page, animation or catalog preview is not proof of a working launcher runtime feature. Treat all statuses, package availability and payment configuration as authoritative only when confirmed by the relevant backend/release.
