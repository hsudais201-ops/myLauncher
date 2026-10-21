# Connect the website to the shared VoidCraft social API

This integration is designed for the existing Cloudflare Pages origin. The account site must proxy /api/* to the Worker so browser cookies stay on the website host, while the Android client uses that exact same HTTPS URL and shared D1 database.

## Pages Function

Copy the catch-all file at pages/functions/api/[[path]].js into the website repository as functions/api/[[path]].js, preserving the relative API path.

In Cloudflare Pages project settings, add an environment variable named VOIDCRAFT_SOCIAL_API_URL for the Production/Preview environment that should be tested. Set it to the Worker origin (HTTPS only, no path), after the Worker has been independently deployed. Keep the Worker variable APP_ORIGIN set to the exact website origin. Redeploy only after code and configuration review.

The proxy keeps the incoming request method, JSON body, headers, cookies, query string, and the Worker response's Set-Cookie header. It does not store credentials. If the API URL is absent or invalid it fails closed with a clear error rather than pretending the service works.

## Website client rules

- Use POST /api/auth/register with JSON fields email, username, and password.
- Use POST /api/auth/login and POST /api/auth/logout for browser sessions. Let the HttpOnly cookie be managed by the browser; do not put the session into localStorage.
- Use GET /api/auth/me on page load to determine the signed-in account. HTTP 401 means signed out; 503 means the API is not configured. Do not replace these messages with a fake success state.
- Reuse /api/players, /api/friends, /api/friends/requests, /api/conversations, /api/conversations/:id/messages, /api/blocks, and /api/reports for the website social pages.
- Render user-provided profile and message strings using text nodes, not HTML insertion.
- A username cannot grant Owner status. Respect the role returned by the server and promote owners only through a separate authenticated administrative process.

## Before public registration

This is not a one-click production release. Set up a development Worker and D1 database, run migrations, deploy the Pages proxy to Preview first, and test registration/login/logout, token expiry, friend request boundaries, messages from both devices, blocking in either direction, report creation, and error states. Add email verification, password reset/recovery, account deletion/export, abuse-report review workflow, and external security review before inviting public users. The worker/API code was committed to GitHub only; no Pages or Worker resource has been created or deployed by this change.
