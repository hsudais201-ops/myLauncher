import { currentUser, json, sameOrigin } from "../auth/_utils.js";

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

    // Checkout is deliberately closed until a payment provider, product catalogue,
    // and server-side entitlement validation are configured. No charge can be made here.
    return json({
      error: "Store checkout is not configured yet. No charge has been made.",
      code: "STORE_CHECKOUT_NOT_CONFIGURED"
    }, 503);
  } catch (error) {
    console.error("VoidCraft store purchase authorization failed", { name: error?.name || "Error" });
    return json({
      error: "The store purchase check could not be completed. No charge has been made.",
      code: "STORE_PURCHASE_CHECK_FAILED"
    }, 503);
  }
}
