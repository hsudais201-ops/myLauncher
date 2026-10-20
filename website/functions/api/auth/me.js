import { currentUser, json, publicUser } from "./_utils.js";

export async function onRequestGet({ request, env }) {
  if (!env?.VOIDCRAFT_AUTH) return json({ error: "Account service is not connected.", code: "AUTH_DB_NOT_BOUND" }, 503);
  try {
    const user = await currentUser(request, env);
    if (!user) return json({ authenticated: false }, 200);
    return json({ authenticated: true, user: publicUser(user) }, 200);
  } catch (error) {
    console.error("VoidCraft session lookup failed", { name: error?.name || "Error" });
    return json({ error: "The profile session could not be checked.", code: "AUTH_SESSION_LOOKUP_FAILED" }, 503);
  }
}