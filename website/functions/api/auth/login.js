import { allowAttempt, constantTimeEqual, createSession, json, passwordHash, publicUser, readJson, sameOrigin, sessionCookie } from "./_utils.js";

const DUMMY_SALT = "8a4f92b61c30e7d5a18f4c09b7d2e603";

export async function onRequestPost({ request, env }) {
  if (!env?.VOIDCRAFT_AUTH) return json({ error: "Account service is temporarily unavailable." }, 503);
  if (!sameOrigin(request)) return json({ error: "Request origin could not be verified." }, 403);

  let limit;
  try {
    limit = await allowAttempt(request, env, "login");
  } catch {
    return json({ error: "Account service is temporarily unavailable." }, 503);
  }
  if (!limit.allowed) return json({ error: "Too many sign-in attempts. Please try again later." }, 429, { "Retry-After": String(limit.retryAfter) });

  const body = await readJson(request);
  const identifier = typeof body?.identifier === "string" ? body.identifier.trim().toLowerCase() : "";
  const password = typeof body?.password === "string" ? body.password : "";
  if (!identifier || identifier.length > 254 || !password || password.length > 128) {
    return json({ error: "Check your username/email and password." }, 400);
  }

  let user;
  try {
    user = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT id, username, email, password_salt, password_hash, created_at FROM users " +
      "WHERE username = ? COLLATE NOCASE OR email = ? COLLATE NOCASE LIMIT 1"
    ).bind(identifier, identifier).first();
  } catch {
    return json({ error: "Account service is temporarily unavailable." }, 503);
  }

  const salt = user?.password_salt || DUMMY_SALT;
  const calculatedHash = await passwordHash(password, salt);
  const valid = Boolean(user) && constantTimeEqual(calculatedHash, user.password_hash);
  if (!valid) return json({ error: "The username/email or password is incorrect." }, 401);

  const lastLoginAt = new Date().toISOString();
  try {
    await env.VOIDCRAFT_AUTH.prepare("UPDATE users SET last_login_at = ? WHERE id = ?")
      .bind(lastLoginAt, user.id).run();
    const token = await createSession(env, user.id);
    user.last_login_at = lastLoginAt;
    return json({ user: publicUser(user), message: "You're signed in." }, 200, {
      "Set-Cookie": sessionCookie(token)
    });
  } catch {
    return json({ error: "Sign-in couldn't be completed. Please try again." }, 500);
  }
}