import { currentUser, json } from "../auth/_utils.js";

export async function onRequestGet({ request, env }) {
  if (!env?.VOIDCRAFT_AUTH) {
    return json({ error: "The store account database is not connected.", code: "STORE_DB_NOT_BOUND" }, 503);
  }
  try {
    const user = await currentUser(request, env);
    if (!user) return json({ items: [], authenticated: false }, 200);

    const result = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT e.item_id AS itemId, i.kind, i.name, e.purchased_at AS purchasedAt " +
      "FROM store_entitlements e JOIN store_items i ON i.id = e.item_id " +
      "WHERE e.user_id = ? ORDER BY e.purchased_at DESC"
    ).bind(user.id).all();
    return json({
      authenticated: true,
      items: (result.results || []).map(row => ({
        itemId: row.itemId,
        kind: row.kind,
        name: row.name,
        purchasedAt: row.purchasedAt
      }))
    }, 200);
  } catch (error) {
    console.error("VoidCraft entitlement read failed", { name: error?.name || "Error" });
    return json({ error: "Your store ownership could not be loaded.", code: "STORE_ENTITLEMENTS_READ_FAILED" }, 503);
  }
}
