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
- Payment checkout, purchase entitlements, direct binary uploads/R2 storage, automated package scanning/signing, email verification, password recovery email delivery, self-service deletion, live server ping, and launcher-linked installation are not yet fully configured. Paid checkout intentionally returns a safe unavailable response and never reports a fake purchase. Do not claim these capabilities are active.
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
- Checkout is currently validation-only. `POST /api/store/checkout` requires a session and validates current server-owned prices, package availability and quantities, then returns a safe not-configured response because a payment provider and entitlement delivery are not configured. It never creates a fake order or claims a charge succeeded.
- The public catalog exposes only a boolean `packageAvailable` for paid listings; it does not return paid package URLs. A future delivery endpoint must check a confirmed payment entitlement before returning a signed/private download URL.


## Website Social Hub and private Chat

The dedicated `chat.html` page adds a Chat link to the shared site navigation and provides player search, friend requests, a friend list, recent conversations, one-to-one text messages, blocking, reporting, and an owner-only report queue. Discord is linked separately and is not used as the chat transport.

Chat uses the existing `VOIDCRAFT_AUTH` D1 binding, website account IDs, and the existing HttpOnly website session. It does not create a second set of chat passwords or read/write browser storage for credentials. Message content is rendered as text, writes require same-origin requests, the API applies per-account rate limits, and only accepted friends may exchange messages.

Before enabling the page in production, apply `migrations/0005_social_chat.sql` to the same D1 database used by the account API. This creates social tables referencing the existing `users(id)` records; it does not change the account schema or seed fake accounts. The Pages Functions route is `/api/chat/*`. After applying the migration, test with two ordinary website accounts: search, send/accept request, message in both directions, block, report, sign out, and sign back in. The owner-only moderation queue appears only for a session whose database role is `owner`.

The chat test suite includes static checks plus a database-backed API flow using an in-memory SQLite adapter to exercise the existing cookie session, player search, friend requests, messaging, blocking, abuse reports, and the owner report queue.

The social page and backend in this change are source updates only until the website branch is merged/deployed and the D1 migration is applied. Do not claim live chat availability before those deployment steps and an end-to-end test.
