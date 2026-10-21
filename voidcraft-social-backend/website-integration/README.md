# Connect the website to the shared VoidCraft social API

This integration is designed for the existing Cloudflare Pages origin. The account site must proxy /api/* to the Worker so browser cookies stay on the website host, while the Android client uses that exact same HTTPS URL and shared D1 database.

## Pages Function

Copy the catch-all file at pages/functions/api/[[path]].js into the website repository as functions/api/[[path]].js, preserving the relative API path.

In Cloudflare Pages project settings, add VOIDCRAFT_SOCIAL_API_URL for the Production/Preview environment being tested. Set it to the Worker origin (HTTPS only, no path), after the Worker has been independently deployed. Also add VOIDCRAFT_PROXY_SECRET as a secret in the Pages environment and as a Worker secret using npx wrangler secret put VOIDCRAFT_PROXY_SECRET. Both sides must use the same randomly generated value (at least 32 characters); never commit it. Keep the Worker variable APP_ORIGIN set to the exact website origin. Redeploy only after code and configuration review.

The proxy overwrites X-VC-Proxy-Secret with the server-side secret and adds X-VC-Client-IP from the Pages request's CF-Connecting-IP. The Worker accepts that IP header only after validating the proxy secret. This matters because Cloudflare may replace CF-Connecting-IP with a proxy IP on cross-zone Worker subrequests; do not trust a client-supplied X-Forwarded-For or X-VC-Client-IP header.

The proxy keeps the incoming request method, JSON body, headers, cookies, query string, and the Worker response's Set-Cookie header. It does not store credentials. If the API URL is absent or invalid it fails closed with a clear error rather than pretending the service works.

## Website client rules

The ready-to-copy browser API client is in website-integration/social-api.js. Import it from the website's module script, create one VoidCraftSocialApi instance, and call its methods below. It uses the same-origin Pages routes and never stores browser session tokens in localStorage.

A responsive proof-of-integration page is included at website-integration/social-page.example.html. It contains real sign-in/registration forms, profile editing, player search, friend requests, friends, recent conversations, private text chat, block/report controls, and the separate Discord community link. Copy it and social-api.js into the website repository after the Pages proxy is configured. The example is not yet part of the live website and will show a service error until the API is deployed.

- Use POST /api/auth/register with JSON fields email, username, and password.
- Use POST /api/auth/login and POST /api/auth/logout for browser sessions. Let the HttpOnly cookie be managed by the browser; do not put the session into localStorage.
- Use GET /api/auth/me on page load to determine the signed-in account. HTTP 401 means signed out; 503 means the API is not configured. Do not replace these messages with a fake success state.
- Reuse /api/players, /api/friends, /api/friends/requests, /api/conversations, /api/conversations/:id/messages, /api/blocks, and /api/reports for the website social pages.
- Render user-provided profile and message strings using text nodes, not HTML insertion.
- A username cannot grant Owner status. Respect the role returned by the server and promote owners only through a separate authenticated administrative process.

## Before public registration

This is not a one-click production release. Set up a development Worker and D1 database, run migrations, deploy the Pages proxy to Preview first, and test registration/login/logout, token expiry, friend request boundaries, messages from both devices, blocking in either direction, report creation, and error states. Add email verification, password reset/recovery, account deletion/export, abuse-report review workflow, and external security review before inviting public users. The worker/API code was committed to GitHub only; no Pages or Worker resource has been created or deployed by this change.


## Add the Social Hub page to the actual website

The file `social.html` is the dedicated website Social Hub page. To make the chat option visible on the live site, copy `social.html` and `social-api.js` into the deployed website's public root (or adapt the import path if your site uses a nested route). Add a visible **Social Hub / Chat** link to the website's main navigation pointing to `/social.html`. The page includes account registration/sign-in, player search, friend requests, friends, recent conversations, and private messaging; it does not use Discord for chat.

For the page to function, deploy the Pages Function proxy at `functions/api/[[path]].js` and configure `VOIDCRAFT_SOCIAL_API_URL` plus the same randomly generated `VOIDCRAFT_PROXY_SECRET` on both the Pages project and Worker. Deploy the Worker and D1 migration, set its `APP_ORIGIN` to the exact production website origin, and run the documented integration tests. Merely copying the HTML will show the interface but cannot make registration or messages work until the backend is deployed and connected.

**Deployment status:** these are integration files in this launcher repository. They have not been deployed to `voidcraft-website.pages.dev`; do not claim the live site is fixed until the actual website repository/project is updated and tested.
