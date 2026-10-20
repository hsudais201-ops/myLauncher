import { currentUser, json, readJson, sameOrigin } from "../auth/_utils.js";

export async function onRequestPost({ request, env }) {
  if (!sameOrigin(request)) return json({ error: "Request origin could not be verified. Reload and retry.", code: "CHECKOUT_ORIGIN_REJECTED" }, 403);
  if (!env?.VOIDCRAFT_AUTH) return json({ error: "The account service is not connected.", code: "AUTH_DB_NOT_BOUND" }, 503);
  try {
    const user = await currentUser(request, env);
    if (!user) return json({
      error: "Sign in is required for purchases. Free downloads remain available without an account.",
      code: "STORE_SIGN_IN_REQUIRED"
    }, 401);

    const body = await readJson(request);
    if (!Array.isArray(body?.items) || body.items.length < 1 || body.items.length > 25) {
      return json({ error: "Your cart is empty or has too many different items.", code: "CART_INVALID" }, 400);
    }
    const requested = new Map();
    for (const entry of body.items) {
      if (!entry || typeof entry.id !== "string" || entry.id.length > 80 ||
          !Number.isInteger(entry.quantity) || entry.quantity < 1 || entry.quantity > 9) {
        return json({ error: "One or more cart entries are invalid.", code: "CART_ITEM_INVALID" }, 400);
      }
      requested.set(entry.id, (requested.get(entry.id) || 0) + entry.quantity);
    }
    if ([...requested.values()].some(quantity => quantity > 9)) {
      return json({ error: "A cart item cannot exceed 9 copies.", code: "CART_QUANTITY_LIMIT" }, 400);
    }

    const validated = [];
    for (const [id, quantity] of requested) {
      const item = await env.VOIDCRAFT_AUTH.prepare(
        "SELECT id, name, kind, status, availability, price_cents, currency, package_url FROM store_items WHERE id = ? LIMIT 1"
      ).bind(id).first();
      if (!item || item.status !== "published") {
        return json({ error: "A cart item is no longer available. Refresh your cart.", code: "CART_ITEM_UNAVAILABLE" }, 409);
      }
      if (item.price_cents === null || Number(item.price_cents) <= 0 ||
          item.availability !== "available" || !item.package_url) {
        return json({ error: "A cart item is a preview or has no released paid package. Remove it or wait for the item to be released.", code: "CART_ITEM_NOT_PURCHASABLE" }, 409);
      }
      validated.push({
        id: item.id,
        name: item.name,
        quantity,
        unitPriceCents: Number(item.price_cents),
        currency: item.currency || "USD"
      });
    }

    // No payment provider is configured. This endpoint validates server-owned prices
    // but never creates an order or claims that a charge has happened.
    const totalCents = validated.reduce((total, item) => total + item.unitPriceCents * item.quantity, 0);
    return json({
      error: "Secure checkout is not configured yet. Your cart is saved and no charge has been made.",
      code: "STORE_CHECKOUT_NOT_CONFIGURED",
      currency: validated[0]?.currency || "USD",
      totalCents,
      items: validated
    }, 503);
  } catch (error) {
    console.error("VoidCraft checkout validation failed", { name: error?.name || "Error" });
    return json({ error: "Checkout validation failed. No charge has been made.", code: "CHECKOUT_VALIDATION_FAILED" }, 503);
  }
}
