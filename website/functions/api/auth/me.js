import { currentUser, json, publicUser } from "./_utils.js";

export async function onRequestGet({ request, env }) {
  if (!env?.VOIDCRAFT_AUTH) return json({ error: "Account service is temporarily unavailable." }, 503);
  try {
    const user = await currentUser(request, env);
    if (!user) return json({ authenticated: false }, 200);
    return json({ authenticated: true, user: publicUser(user) }, 200);
  } catch {
    return json({ error: "Account service is temporarily unavailable." }, 503);
  }
}