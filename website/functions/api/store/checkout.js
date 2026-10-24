import { currentUser, json, readJson, sameOrigin } from "../auth/_utils.js";
import { createStripeCheckoutSession } from "./_fulfillment.js";

export async function onRequestPost({ request, env }) {
  if (!sameOrigin(request)) {
    return json({ error: "Request origin could not be verified. Reload and retry.", code: "CHECKOUT_ORIGIN_REJECTED" }, 403);
  }
  if (!env?.VOIDCRAFT_AUTH) {
    return json({ error: "The account service is not connected.", code: "AUTH_DB_NOT_BOUND" }, 503);
  }
  if (!env?.STRIPE_SECRET_KEY || !env?.STRIPE_WEBHOOK_SECRET) {
    return json({
      error: "Secure payments are not configured yet. No charge has been made, and no cosmetic has been unlocked.",
      code: "STRIPE_NOT_CONFIGURED"
    }, 503);
  }

  try {
    const user = await currentUser(request, env);
    if (!user) {
      return json({
        error: "Sign in is required for purchases. Free downloads remain available without an account.",
        code: "STORE_SIGN_IN_REQUIRED"
      }, 401);
    }

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
        kind: item.kind,
        quantity,
        unitPriceCents: Number(item.price_cents),
        currency: String(item.currency || "USD").toUpperCase()
      });
    }

    const currency = validated[0].currency;
    if (validated.some(item => item.currency !== currency)) {
      return json({ error: "Checkout requires all items to use the same currency. Please split your cart by currency.", code: "CART_CURRENCY_MIXED" }, 400);
    }

    const totalCents = validated.reduce((total, item) => total + item.unitPriceCents * item.quantity, 0);
    if (!Number.isSafeInteger(totalCents) || totalCents <= 0) {
      return json({ error: "The order total is invalid.", code: "CART_TOTAL_INVALID" }, 400);
    }

    const now = new Date().toISOString();
    const orderId = crypto.randomUUID();
    const orderItems = JSON.stringify(validated);
    await env.VOIDCRAFT_AUTH.prepare(
      "INSERT INTO store_orders (id, user_id, status, items_json, currency, total_cents, created_at, updated_at) VALUES (?, ?, 'creating', ?, ?, ?, ?, ?)"
    ).bind(orderId, user.id, orderItems, currency, totalCents, now, now).run();

    const form = new URLSearchParams();
    const origin = new URL(request.url).origin;
    form.set("mode", "payment");
    form.set("success_url", origin + "/checkout.html?purchase=success&session_id={CHECKOUT_SESSION_ID}");
    form.set("cancel_url", origin + "/checkout.html?purchase=cancelled");
    form.set("client_reference_id", user.id);
    form.set("metadata[order_id]", orderId);
    form.set("metadata[user_id]", user.id);
    form.set("payment_intent_data[metadata][order_id]", orderId);
    form.set("payment_intent_data[metadata][user_id]", user.id);

    validated.forEach((item, index) => {
      const prefix = "line_items[" + index + "]";
      form.set(prefix + "[quantity]", String(item.quantity));
      form.set(prefix + "[price_data][currency]", item.currency.toLowerCase());
      form.set(prefix + "[price_data][unit_amount]", String(item.unitPriceCents));
      form.set(prefix + "[price_data][product_data][name]", item.name.slice(0, 250));
      form.set(prefix + "[price_data][product_data][metadata][store_item_id]", item.id);
      form.set(prefix + "[price_data][product_data][metadata][kind]", item.kind);
    });

    const created = await createStripeCheckoutSession(env, form);
    if (!created.ok) {
      await env.VOIDCRAFT_AUTH.prepare(
        "UPDATE store_orders SET status = 'failed', updated_at = ? WHERE id = ?"
      ).bind(new Date().toISOString(), orderId).run();
      return json({ error: created.error, code: created.code }, created.status);
    }

    const session = created.session;
    const updatedAt = new Date().toISOString();
    await env.VOIDCRAFT_AUTH.prepare(
      "UPDATE store_orders SET stripe_session_id = ?, status = 'pending', updated_at = ? WHERE id = ? AND user_id = ?"
    ).bind(session.id, updatedAt, orderId, user.id).run();

    return json({
      checkoutUrl: session.url,
      sessionId: session.id,
      currency: currency,
      totalCents
    }, 200);
  } catch (error) {
    console.error("VoidCraft checkout creation failed", { name: error?.name || "Error" });
    return json({
      error: "Secure checkout could not be started. No charge has been made.",
      code: "CHECKOUT_CREATE_FAILED"
    }, 503);
  }
}
