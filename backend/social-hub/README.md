# Voidcraft Social Hub API (staging)

This Worker is the staging backend for Voidcraft Social Hub, bound to the D1 database `voidcraft-auth-staging-socialhub`. Do not bind it to the production `voidcraft-auth` database.

## Endpoints

- `GET /api/health` — service health.
- `POST /api/register` — username and password account registration.
- `POST /api/login`, `POST /api/logout`, `GET /api/me` — bearer-token sessions.
- `GET /api/friends` — friends, incoming/outgoing requests, and block list.
- `POST /api/friends/requests` — send a request by username.
- `POST /api/friends/requests/{uuid}/respond` — accept, reject, or cancel.
- `POST /api/friends/remove` — remove a friend.
- `POST /api/blocks`, `DELETE /api/blocks/{username}` — block/unblock.
- `GET /api/messages?with=username&after=timestamp`, `POST /api/messages` — direct messages.
- `POST /api/reports` — report a user for moderation review.

Messages are delivered by polling. This initial backend does not use WebSockets or push notifications.

## Security features in this beta

- PBKDF2-HMAC-SHA-256 password hashes (120,000 iterations) with a random per-user salt.
- Random 256-bit bearer session tokens; only SHA-256 token hashes are stored; sessions expire after 30 days.
- SQL parameters are bound, and request bodies have a 16 KiB limit.
- Registration/login, friend requests, messages, and reports are rate-limited.
- Messages require an accepted friendship; block checks take precedence.
- Block and report endpoints are included.
- The API does not collect email addresses yet. Because the existing schema requires a unique email, it uses a reserved non-deliverable `.invalid` address internally. Email verification and password recovery are not implemented.
- Do not reuse passwords from other accounts. Before public launch, add verified account recovery, abuse review tools, privacy/retention and deletion policies, automated security tests, and monitoring.

## Database

`schema.sql` contains idempotent schema statements for staging. Existing staging tables are retained. The Worker must never be deployed against the production database without a separate security review.
