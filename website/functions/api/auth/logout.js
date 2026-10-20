import { clearSessionCookie, deleteSession, json, sameOrigin } from "./_utils.js";

export async function onRequestPost({ request, env }) {
  if (!sameOrigin(request)) return json({ error: "Request origin could not be verified." }, 403, { "Set-Cookie": clearSessionCookie() });
  try {
    await deleteSession(request, env);
  } catch {
    return json({ error: "Sign-out could not be confirmed. Close the browser to end this session." }, 503, { "Set-Cookie": clearSessionCookie() });
  }
  return json({ message: "You're signed out." }, 200, { "Set-Cookie": clearSessionCookie() });
}