# VoidCraft store payment setup

The storefront only unlocks cosmetics from server-confirmed paid orders. Never unlock an item based on a client-side flag or a return URL alone.

## Required Cloudflare Pages secrets

In the Cloudflare dashboard, open **Workers & Pages → voidcraft-launcher → Settings → Variables and Secrets**. Add these as encrypted production secrets (use test-mode credentials first):

- \`STRIPE_SECRET_KEY\`: Stripe Checkout secret key (\`sk_test_...\` for test mode, \`sk_live_...\` only after go-live review).
- \`STRIPE_WEBHOOK_SECRET\`: signing secret Stripe shows for the webhook endpoint below (\`whsec_...\`).

The current Pages production project has no Stripe environment variables configured, so checkout stays disabled with a clear no-charge message until both secrets are added.

## Stripe webhook

Create a Stripe webhook endpoint:

\`https://voidcraft-launcher.pages.dev/api/store/stripe-webhook\`

Subscribe to:
- \`checkout.session.completed\`
- \`checkout.session.async_payment_succeeded\`
- \`checkout.session.async_payment_failed\`
- \`checkout.session.expired\`

Copy the endpoint signing secret into \`STRIPE_WEBHOOK_SECRET\`. The webhook validates Stripe's HMAC signature and grants account ownership only after Stripe reports the Checkout Session as paid. The checkout-return endpoint independently verifies the session before showing the purchase-confirmed state.

## Product readiness

A cosmetic can be purchased only when its server catalog entry has all of the following:
- \`kind = 'cosmetic'\`
- \`status = 'published'\`
- \`availability = 'available'\`
- a positive \`price_cents\`
- a released \`package_url\`

The seeded cosmetics are concept previews with no price or package, so they remain non-purchasable until a real release and price are configured. The Equip action is revealed for that SKU once the signed-in user has a matching server-side entitlement.

## Scope

The current Equip action saves the owned cosmetic selection for this VoidCraft website profile. It does not draw a Minecraft player-model cosmetic. That requires the separate compatible client renderer and, for multiplayer visibility, compatible server/client support.
