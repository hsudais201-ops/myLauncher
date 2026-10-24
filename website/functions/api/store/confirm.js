import { currentUser, json, sameOrigin } from "../auth/_utils.js";
import { fulfillPaidSession, retrieveStripeSession } from "./_fulfillment.js";

export async function onRequestGet({ request, env }) {
  if (!sameOrigin(request)) {
    return json({ error: "Request origin could not be verified. Reload and retry.", code: "STORE_CONFIRM_ORIGIN_REJECTED" }, 403);
  }
  if (!env?.VOIDCRAFT_AUTH) return json({ error: "The store account database is not connected.", code: "STORE_DB_NOT_BOUND" }, 503);

  try {
    const user = await currentUser(request, env);
    if (!user) return json({ error: "Sign in again to confirm this purchase.", code: "STORE_SIGN_IN_REQUIRED" }, 401);

    const sessionId = new URL(request.url).searchParams.get("session_id") || "";
    if (!/^cs_(test|live)_[A-Za-z0-9]+$/.test(sessionId) || sessionId.length > 255) {
      return json({ error: "The checkout session ID is invalid.", code: "STORE_SESSION_ID_INVALID" }, 400);
    }

    const session = await retrieveStripeSession(env, sessionId);
    if (session.metadata?.user_id !== user.id || session.client_reference_id !== user.id) {
      return json({ error: "This checkout session belongs to a different account.", code: "STORE_SESSION_OWNER_MISMATCH" }, 403);
    }

    const result = await fulfillPaidSession(env, session);
    if (!result.ok) return json({ error: result.error, code: result.code }, result.status);

    const cosmetics = result.items.filter(item => item.kind === "cosmetic");
    return json({
      confirmed: true,
      orderId: result.orderId,
      paidAt: result.paidAt,
      currency: session.currency ? session.currency.toUpperCase() : "USD",
      totalCents: Number(session.amount_total || 0),
      items: result.items,
      cosmeticItems: cosmetics,
      hasCosmetics: cosmetics.length > 0
    }, 200);
  } catch (error) {
    const configured = error?.message === "STRIPE_NOT_CONFIGURED";
    console.error("VoidCraft purchase confirmation failed", { name: error?.name || "Error" });
    return json({
      error: configured
        ? "Secure payments are not configured yet. No cosmetic has been unlocked."
        : "The payment confirmation could not be checked. Refresh this page and try again.",
      code: configured ? "STRIPE_NOT_CONFIGURED" : "STORE_CONFIRM_FAILED"
    }, configured ? 503 : (error?.status || 503));
  }
}
