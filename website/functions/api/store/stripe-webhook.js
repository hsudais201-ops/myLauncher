import { constantTimeEqual, json } from "../auth/_utils.js";
import { fulfillPaidSession } from "./_fulfillment.js";

function toHex(buffer) {
  return Array.from(new Uint8Array(buffer), byte => byte.toString(16).padStart(2, "0")).join("");
}

async function verifyStripeSignature(payload, signatureHeader, secret) {
  if (!signatureHeader || !secret) return false;
  const parts = signatureHeader.split(",").map(part => part.trim().split("="));
  const timestamps = parts.filter(([key]) => key === "t").map(([, value]) => value);
  const signatures = parts.filter(([key]) => key === "v1").map(([, value]) => value);
  const timestamp = timestamps[0];
  if (!timestamp || !/^\d+$/.test(timestamp) || signatures.length === 0) return false;
  if (Math.abs(Math.floor(Date.now() / 1000) - Number(timestamp)) > 300) return false;

  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const expected = toHex(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(timestamp + "." + payload)));
  return signatures.some(signature => constantTimeEqual(expected, signature));
}

export async function onRequestPost({ request, env }) {
  if (!env?.VOIDCRAFT_AUTH) return json({ error: "The store database is not connected." }, 503);
  if (!env?.STRIPE_WEBHOOK_SECRET) return json({ error: "Stripe webhook signing is not configured." }, 503);

  const payload = await request.text();
  if (payload.length > 1024 * 1024) return json({ error: "Webhook payload is too large." }, 413);
  const signature = request.headers.get("Stripe-Signature") || "";
  try {
    if (!await verifyStripeSignature(payload, signature, env.STRIPE_WEBHOOK_SECRET)) {
      return json({ error: "Stripe signature verification failed." }, 400);
    }
    let event;
    try { event = JSON.parse(payload); }
    catch { return json({ error: "Invalid Stripe event JSON." }, 400); }
    if (typeof event?.id !== "string" || typeof event?.type !== "string") {
      return json({ error: "Invalid Stripe event envelope." }, 400);
    }

    const existing = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT id FROM store_webhook_events WHERE id = ? LIMIT 1"
    ).bind(event.id).first();
    if (existing) return json({ received: true, alreadyProcessed: true }, 200);

    if (event.type === "checkout.session.completed" || event.type === "checkout.session.async_payment_succeeded") {
      const session = event.data?.object;
      if (!session || session.payment_status !== "paid") {
        // Delayed payment methods send async_payment_succeeded if payment later clears.
        return json({ received: true, awaitingPayment: true }, 200);
      }
      const result = await fulfillPaidSession(env, session);
      if (!result.ok) {
        console.error("Stripe purchase fulfillment is pending", { code: result.code, eventId: event.id });
        return json({ error: result.error, code: result.code }, 500);
      }
    } else if (event.type === "checkout.session.expired") {
      const session = event.data?.object;
      const orderId = session?.metadata?.order_id;
      if (typeof orderId === "string") {
        await env.VOIDCRAFT_AUTH.prepare(
          "UPDATE store_orders SET status = 'canceled', updated_at = ? WHERE id = ? AND status = 'pending'"
        ).bind(new Date().toISOString(), orderId).run();
      }
    } else if (event.type === "checkout.session.async_payment_failed") {
      const session = event.data?.object;
      const orderId = session?.metadata?.order_id;
      if (typeof orderId === "string") {
        await env.VOIDCRAFT_AUTH.prepare(
          "UPDATE store_orders SET status = 'failed', updated_at = ? WHERE id = ? AND status = 'pending'"
        ).bind(new Date().toISOString(), orderId).run();
      }
    }

    await env.VOIDCRAFT_AUTH.prepare(
      "INSERT OR IGNORE INTO store_webhook_events (id, event_type, processed_at) VALUES (?, ?, ?)"
    ).bind(event.id, event.type, new Date().toISOString()).run();
    return json({ received: true }, 200);
  } catch (error) {
    console.error("Stripe webhook processing failed", { name: error?.name || "Error" });
    return json({ error: "Webhook processing failed; Stripe may retry this event." }, 500);
  }
}
