import { json } from "./_utils.js";

export async function onRequestGet({ env }) {
  if (!env?.VOIDCRAFT_AUTH) return json({ ok: false, service: "account", code: "AUTH_DB_NOT_BOUND" }, 503);
  try {
    const check = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT COUNT(*) AS table_count FROM sqlite_master WHERE type = 'table' AND name IN ('users', 'sessions', 'auth_rate_limits')"
    ).first();
    if (Number(check?.table_count || 0) !== 3) {
      return json({ ok: false, service: "account", code: "AUTH_DB_SCHEMA_INCOMPLETE" }, 503);
    }
    await env.VOIDCRAFT_AUTH.prepare("SELECT 1 AS ok").first();
    return json({ ok: true, service: "account", database: "ready" }, 200);
  } catch (error) {
    console.error("VoidCraft account health check failed", { name: error?.name || "Error" });
    return json({ ok: false, service: "account", code: "AUTH_DB_QUERY_FAILED" }, 503);
  }
}