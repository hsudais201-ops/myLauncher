# VoidCraft Social Backend (development milestone)

This folder contains a separate Cloudflare Worker + D1 API for **VoidCraft-owned accounts and in-app social features**. It is not Discord chat and does not use Minecraft/Microsoft credentials. The launcher and official website must call this same account database to see the same profile, friend list, and private-message history.

## Status

This is an API implementation milestone, **not a deployed or public-launch-ready service**. It has API route/schema smoke tests, but still needs a real D1 instance, Pages website integration, end-to-end browser/Android testing, email verification and account recovery, and a security review before public registration is enabled. No Cloudflare account resources were created or changed by committing these source files.

Messages are persisted and retrieved through REST endpoints. Clients should refresh an open conversation periodically (for example, every 3–5 seconds). This milestone does not implement WebSocket push, live-presence indicators, read receipts, image/file messages, or moderation tooling.

## API overview

All responses are JSON and use Cache-Control: no-store. User IDs are server-assigned numeric IDs; clients must never supply their own role or permission.

### Authentication

Website endpoints use an HttpOnly; Secure; SameSite=Strict session cookie and require same-origin browser requests:

- POST /api/auth/register — { "email": "...", "username": "...", "password": "..." }
- POST /api/auth/login — { "identifier": "email or username", "password": "..." }
- POST /api/auth/logout
- GET /api/auth/me
- GET /api/profile
- PATCH /api/profile — displayName (max 32) and/or bio (max 280)

Launcher endpoints use the same users and sessions tables but return a random bearer token once at login/registration:

- POST /api/launcher/auth/register
- POST /api/launcher/auth/login

The launcher must send Authorization: Bearer accessToken on future requests, store it with Android's encrypted credential storage, and clear it on sign-out. Do not store passwords or tokens in plain shared preferences or logs. Use the account API's returned user.role to decide whether to show a server-granted owner/moderator marker; the username alone grants no privileges.

### Friends and messaging

- GET /api/players?q=player — search usernames by prefix (minimum 3 characters).
- GET /api/friends
- GET /api/friends/requests
- POST /api/friends/requests — username in JSON
- POST /api/friends/requests/:requestId/accept
- DELETE /api/friends/requests/:requestId — decline an incoming request.
- DELETE /api/friends/:userId — remove friend.
- GET /api/conversations
- GET /api/conversations/:userId/messages?limit=40&before=123
- POST /api/conversations/:userId/messages — content in JSON
- GET /api/blocks
- POST /api/blocks/:userId
- DELETE /api/blocks/:userId
- POST /api/reports — targetUserId, category, and optional details

Only accepted friends can exchange messages. Server-side checks block users, block message delivery when either user blocks the other, restrict friend requests, validate IDs and message sizes, and rate-limit account creation/login, friend requests, message sends, and reports. Messages are plain text: website clients must render body text as text, never insert it as HTML.

Report submissions are stored for review, but this milestone has no moderation dashboard or report-notification workflow. Do not expose public registration until a responsible operator can review reports and respond to abuse.

## Data and security decisions

- Passwords are never stored in clear text; the worker uses a random salt and PBKDF2-HMAC-SHA-256 hash.
- Session bearer tokens are random and only their SHA-256 digests are stored in D1.
- Browser authentication uses an HttpOnly cookie, not localStorage.
- CORS is restricted to APP_ORIGIN; there is no wildcard origin.
- API inputs are bounded and SQL values are passed with prepared-statement binding.
- The schema relies on Cloudflare D1's default foreign-key enforcement; it does not try to toggle the foreign-key PRAGMA from inside a migration.
- Password and session hashes, email addresses, and bearer tokens must not be logged.
- New accounts always start with the member role. Do not auto-promote a username (including mflegend) from a user-controlled field. Owner promotion must happen through a separately authenticated administrative process after verifying account ownership.
- Email verification, password-reset email delivery, account deletion/export, report moderation, and account lock/appeal flows remain required before public release.

## Deploying a development API

Use a development Cloudflare account/environment first, not the live website:

1. Install Node.js 22 and Wrangler (npx wrangler).
2. From this folder, create the D1 database: npx wrangler d1 create voidcraft-social.
3. Put the returned database ID in wrangler.toml, replacing REPLACE_WITH_D1_DATABASE_ID.
4. Apply the schema to the development database: npx wrangler d1 migrations apply voidcraft-social --remote.
5. Run npm run verify, then start a local worker with npx wrangler dev.
6. After reviewing the environment and tests, deploy only to a development Worker with npx wrangler deploy.

Do not point a publicly advertised website at the API yet. For cookie-based browser sign-in, the API must be mounted under the same origin as the website (for example as a Cloudflare Pages Function under https://voidcraft-website.pages.dev/api/*); a standalone workers.dev host is a different site for browser-cookie purposes. The current website repository is not writable through the current GitHub connection, so the Pages Function/proxy and UI integration have not been installed into the live website. Keep APP_ORIGIN set to the exact production website origin and never use * with credentials.

## Local checks

From this folder, run npm run verify.

The checks validate JavaScript syntax, a health/CORS smoke path, origin rejection, and the required D1 tables/constraints. These checks are not end-to-end proof of account registration, message delivery, or deployment; those need tests against a temporary D1 database and the actual website/launcher clients.
