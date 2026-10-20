import { clearSessionCookie, deleteSession, json, sameOrigin } from "./_utils.js";

export async function onRequestPost({ request, env }) {
  if (!sameOrigin(request)) {
    return json({ error: "Request origin could not be verified. Reload the page and try again.", code: "AUTH_ORIGIN_REJECTED" }, 403);
  }
  try {
    await deleteSession(request, env);
    return json({ message: "You're signed out." }, 200, { "Set-Cookie": clearSessionCookie() });
  } catch (error) {
    console.error("VoidCraft sign-out failed", { name: error?.name || "Error" });
    return json({ error: "Sign-out couldn't be completed. Try again.", code: "AUTH_LOGOUT_FAILED" }, 503);
  }
}