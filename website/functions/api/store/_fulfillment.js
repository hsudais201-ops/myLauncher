export async function createStripeCheckoutSession(env, body) {
  const secret = env?.STRIPE_SECRET_KEY;
  if (typeof secret !== "string" || !secret.startsWith("sk_")) {
    return { ok: false, status: 503, error: "Secure payments are not configured yet. No charge has been made.", code: "STRIPE_NOT_CONFIGURED" };
  }

  const response = await fetch("https://api.stripe.com/v1/checkout/sessions", {
    method: "POST",
    headers: {
      "Authorization": "Bearer " + secret,
      "Content-Type": "application/x-www-form-urlencoded",
      "Accept": "application/json"
    },
    body: body.toString()
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok || !data.id || !data.url) {
    console.error("Stripe Checkout session creation failed", {
      status: response.status,
      code: data?.error?.code || "STRIPE_SESSION_CREATE_FAILED"
    });
    return { ok: false, status: 502, error: "The payment provider could not start checkout. No charge has been made.", code: "STRIPE_SESSION_CREATE_FAILED" };
  }
  try {
    const url = new URL(data.url);
    if (url.protocol !== "https:" || url.hostname !== "checkout.stripe.com") {
      return { ok: false, status: 502, error: "The payment provider returned an invalid checkout URL.", code: "STRIPE_CHECKOUT_URL_INVALID" };
    }
  } catch {
    return { ok: false, status: 502, error: "The payment provider returned an invalid checkout URL.", code: "STRIPE_CHECKOUT_URL_INVALID" };
  }
  return { ok: true, session: data };
}

export async function retrieveStripeSession(env, sessionId) {
  const secret = env?.STRIPE_SECRET_KEY;
  if (typeof secret !== "string" || !secret.startsWith("sk_")) {
    throw new Error("STRIPE_NOT_CONFIGURED");
  }
  const response = await fetch("https://api.stripe.com/v1/checkout/sessions/" + encodeURIComponent(sessionId), {
    method: "GET",
    headers: { "Authorization": "Bearer " + secret, "Accept": "application/json" }
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok || !data.id) {
    const error = new Error("STRIPE_SESSION_LOOKUP_FAILED");
    error.status = response.status === 404 ? 404 : 502;
    throw error;
  }
  return data;
}

export async function fulfillPaidSession(env, session) {
  const db = env?.VOIDCRAFT_AUTH;
  if (!db) return { ok: false, status: 503, error: "The store account database is not connected.", code: "STORE_DB_NOT_BOUND" };
  if (!session || session.mode !== "payment" || session.payment_status !== "paid") {
    return { ok: false, status: 409, error: "Payment has not been confirmed yet. No cosmetic has been unlocked.", code: "PAYMENT_NOT_CONFIRMED" };
  }

  const orderId = typeof session.metadata?.order_id === "string" ? session.metadata.order_id : "";
  if (!orderId) return { ok: false, status: 400, error: "The checkout session has no valid store order.", code: "STORE_ORDER_MISSING" };

  const order = await db.prepare(
    "SELECT id, user_id, stripe_session_id, status, items_json, currency, total_cents FROM store_orders WHERE id = ? LIMIT 1"
  ).bind(orderId).first();
  if (!order || order.stripe_session_id !== session.id) {
    return { ok: false, status: 409, error: "The checkout session is not linked to a saved store order.", code: "STORE_ORDER_MISMATCH" };
  }
  if (session.metadata?.user_id !== order.user_id || session.client_reference_id !== order.user_id) {
    return { ok: false, status: 403, error: "The checkout account does not match this store order.", code: "STORE_ORDER_OWNER_MISMATCH" };
  }
  if (Number(session.amount_total) !== Number(order.total_cents) ||
      String(session.currency || "").toUpperCase() !== String(order.currency || "").toUpperCase()) {
    return { ok: false, status: 409, error: "The confirmed payment does not match the saved order total.", code: "STORE_ORDER_TOTAL_MISMATCH" };
  }

  let purchasedItems;
  try {
    purchasedItems = JSON.parse(order.items_json);
  } catch {
    return { ok: false, status: 500, error: "The store order could not be read safely.", code: "STORE_ORDER_CORRUPT" };
  }
  if (!Array.isArray(purchasedItems) || purchasedItems.length < 1 || purchasedItems.length > 25 ||
      purchasedItems.some(item => !item || typeof item.id !== "string" || !["cosmetic", "feature"].includes(item.kind))) {
    return { ok: false, status: 500, error: "The saved store order is invalid.", code: "STORE_ORDER_CORRUPT" };
  }

  const purchasedAt = new Date().toISOString();
  const statements = [
    db.prepare(
      "UPDATE store_orders SET status = 'paid', stripe_payment_intent = ?, paid_at = ?, updated_at = ? WHERE id = ?"
    ).bind(typeof session.payment_intent === "string" ? session.payment_intent : null, purchasedAt, purchasedAt, order.id)
  ];
  for (const item of purchasedItems) {
    statements.push(db.prepare(
      "INSERT OR IGNORE INTO store_entitlements (user_id, item_id, order_id, purchased_at) VALUES (?, ?, ?, ?)"
    ).bind(order.user_id, item.id, order.id, purchasedAt));
  }
  await db.batch(statements);

  return {
    ok: true,
    orderId: order.id,
    userId: order.user_id,
    paidAt: purchasedAt,
    items: purchasedItems.map(item => ({
      id: item.id,
      name: item.name,
      kind: item.kind,
      quantity: Number(item.quantity) || 1
    }))
  };
}
