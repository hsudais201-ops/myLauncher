# VoidCraft Social Hub Worker (staging)

This directory tracks the Worker currently used by the Social Hub browser beta. It is deliberately bound to the isolated `voidcraft-auth-staging-socialhub` D1 database and sets `APP_ENV = "staging"`.

## Fix included

Cloudflare Workers rejects a single PBKDF2 call above 100,000 iterations. Every individual pass is now capped at 100,000. New registrations use a versioned, six-pass chained PBKDF2-SHA-256 hash. Existing unversioned hashes are checked with the legacy single-pass algorithm at 100,000 iterations and upgraded only after a successful login.

The generic error shown to users remains generic, while server logs include the route and exception name/message to assist debugging without returning internal error details to clients.

## Deploy to staging only

From the repository root, after confirming the Cloudflare account is the expected one:

```sh
npx wrangler deploy --config workers/social-hub/wrangler.toml
```

Do not change the D1 binding or `APP_ENV` to production without reviewing and testing the production schema and rollout separately. Use unique beta passwords; this service does not provide email recovery or end-to-end encrypted messages yet.

## Tests

```sh
node --test workers/social-hub/tests/*.test.mjs
```
