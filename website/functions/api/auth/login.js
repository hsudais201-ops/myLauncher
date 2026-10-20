import { allowAttempt, constantTimeEqual, createSession, json, passwordHash, publicUser, readJson, sameOrigin, sessionCookie } from "./_utils.js";
const DUMMY_SALT = "8a4f92b61c30e7d5a18f4c09b7d2e603";

export async function onRequestPost({ request, env }) {
  let stage = "request_start";
  try {
    stage = "check_binding";
    if (!env?.VOIDCRAFT_AUTH) return json({ error: "Account service is not connected. Please try again later.", code: "AUTH_DB_NOT_BOUND" }, 503);
    stage = "check_origin";
    if (!sameOrigin(request)) return json({ error: "Request origin could not be verified. Reload the page and try again.", code: "AUTH_ORIGIN_REJECTED" }, 403);
    stage = "rate_limit";
    const limit = await allowAttempt(request, env, "login");
    if (!limit.allowed) return json({ error: "Too many sign-in attempts. Please try again later.", code: "AUTH_RATE_LIMITED" }, 429, { "Retry-After": String(limit.retryAfter) });

    stage = "read_body";
    const body = await readJson(request);
    const identifier = typeof body?.identifier === "string" ? body.identifier.trim() : "";
    const password = typeof body?.password === "string" ? body.password : "";
    if (!identifier || identifier.length > 254 || !password || password.length > 128) {
      return json({ error: "Check your username/email and password.", code: "AUTH_CREDENTIALS_MISSING" }, 400);
    }

    stage = "read_user";
    let user;
    try {
      user = await env.VOIDCRAFT_AUTH.prepare(
        "SELECT id, username, email, password_salt, password_hash, created_at FROM users " +
        "WHERE username = ? COLLATE NOCASE OR email = ? COLLATE NOCASE LIMIT 1"
      ).bind(identifier, identifier).first();
    } catch (error) {
      console.error("VoidCraft sign-in database read failed", { name: error?.name || "Error" });
      return json({ error: "The account database couldn't be read. Please try again later.", code: "AUTH_LOGIN_DB_READ_FAILED" }, 503);
    }

    stage = "password_hash";
    const calculatedHash = await passwordHash(password, user?.password_salt || DUMMY_SALT);
    if (!user || !constantTimeEqual(calculatedHash, user.password_hash)) {
      return json({ error: "The username/email or password is incorrect.", code: "AUTH_CREDENTIALS_INVALID" }, 401);
    }

    stage = "update_last_login";
    const lastLoginAt = new Date().toISOString();
    await env.VOIDCRAFT_AUTH.prepare("UPDATE users SET last_login_at = ? WHERE id = ?").bind(lastLoginAt, user.id).run();
    stage = "create_session";
    const token = await createSession(env, user.id);
    user.last_login_at = lastLoginAt;
    return json({ user: publicUser(user), message: "You're signed in." }, 200, { "Set-Cookie": sessionCookie(token) });
  } catch (error) {
    console.error("VoidCraft sign-in request failed", { stage, name: error?.name || "Error" });
    return json({ error: "The sign-in service failed during " + stage.replace(/_/g, " ") + ". Please retry after refreshing.", code: "AUTH_LOGIN_INTERNAL_" + stage.toUpperCase() + "_" + String(error?.name || "ERROR").toUpperCase() }, 500);
  }
}