import { currentUser, json, readJson, sameOrigin } from "../auth/_utils.js";

export async function onRequestPost({ request, env }) {
  if (!sameOrigin(request)) {
    return json({ error: "Request origin could not be verified. Reload the page and try again.", code: "STORE_ORIGIN_REJECTED" }, 403);
  }
  if (!env?.VOIDCRAFT_AUTH) {
    return json({ error: "The account service is not connected.", code: "AUTH_DB_NOT_BOUND" }, 503);
  }
  try {
    const user = await currentUser(request, env);
    if (!user) {
      return json({
        error: "Sign in is required to purchase store items. Free downloads do not require an account.",
        code: "STORE_SIGN_IN_REQUIRED"
      }, 401);
    }

    const body = await readJson(request);
    const itemId = typeof body?.itemId === "string" ? body.itemId.trim() : "";
    if (!itemId || itemId.length > 80) {
      return json({ error: "Choose a valid store item.", code: "STORE_ITEM_ID_INVALID" }, 400);
    }
    const item = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT id, name, status, availability, price_cents, currency, package_url FROM store_items WHERE id = ? LIMIT 1"
    ).bind(itemId).first();
    if (!item || item.status !== "published") {
      return json({ error: "This item is not available in the public store.", code: "STORE_ITEM_NOT_FOUND" }, 404);
    }
    if (item.price_cents === null || Number(item.price_cents) === 0) {
      return json({ error: "This item is not a paid purchase. Use its free download link when one is available.", code: "STORE_ITEM_NOT_PAID" }, 400);
    }
    if (item.availability !== "available" || !item.package_url) {
      return json({ error: "This item is a preview or is not ready for distribution. No charge has been made.", code: "STORE_ITEM_NOT_AVAILABLE" }, 409);
    }

    // Real checkout is intentionally unavailable until a payment provider and
    // server-side entitlement delivery are configured. Never report a fake purchase.
    return json({
      error: "Checkout is not configured yet. No charge has been made. This item can be purchased after secure payment setup is complete.",
      code: "STORE_CHECKOUT_NOT_CONFIGURED",
      item: { id: item.id, name: item.name, currency: item.currency, priceCents: Number(item.price_cents) }
    }, 503);
  } catch (error) {
    console.error("VoidCraft store purchase authorization failed", { name: error?.name || "Error" });
    return json({
      error: "The store purchase check could not be completed. No charge has been made.",
      code: "STORE_PURCHASE_CHECK_FAILED"
    }, 503);
  }
}
