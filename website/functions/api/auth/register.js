import { allowAttempt, createSession, json, passwordHash, publicUser, randomHex, readJson, sameOrigin, sessionCookie } from "./_utils.js";

export async function onRequestPost({ request, env }) {
  let stage = "request_start";
  try {
    stage = "check_binding";
    if (!env?.VOIDCRAFT_AUTH) return json({ error: "Account service is not connected. Please try again later.", code: "AUTH_DB_NOT_BOUND" }, 503);
    stage = "check_origin";
    if (!sameOrigin(request)) return json({ error: "Request origin could not be verified. Reload the page and try again.", code: "AUTH_ORIGIN_REJECTED" }, 403);
    stage = "rate_limit";
    const limit = await allowAttempt(request, env, "register");
    if (!limit.allowed) return json({ error: "Too many attempts. Please try again later.", code: "AUTH_RATE_LIMITED" }, 429, { "Retry-After": String(limit.retryAfter) });

    stage = "read_body";
    const body = await readJson(request);
    if (!body) return json({ error: "The registration form could not be read. Reload the page and try again.", code: "AUTH_INVALID_JSON" }, 400);

    const username = typeof body.username === "string" ? body.username.trim() : "";
    const email = typeof body.email === "string" ? body.email.trim().toLowerCase() : "";
    const password = typeof body.password === "string" ? body.password : "";
    if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) {
      return json({ error: "Username must be 3–20 characters and use only letters, numbers, or underscores.", code: "AUTH_USERNAME_INVALID" }, 400);
    }
    if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      return json({ error: "Enter a valid email address.", code: "AUTH_EMAIL_INVALID" }, 400);
    }
    if (password.length < 12 || password.length > 128) {
      return json({ error: "Choose a password between 12 and 128 characters.", code: "AUTH_PASSWORD_INVALID" }, 400);
    }

    stage = "password_hash";
    const salt = randomHex(16);
    const hash = await passwordHash(password, salt);
    const id = crypto.randomUUID();
    const createdAt = new Date().toISOString();
    stage = "insert_user";
    try {
      await env.VOIDCRAFT_AUTH.prepare(
        "INSERT INTO users (id, username, email, password_salt, password_hash, created_at) VALUES (?, ?, ?, ?, ?, ?)"
      ).bind(id, username, email, salt, hash, createdAt).run();
    } catch (error) {
      const message = String(error?.message || "");
      if (/unique|constraint/i.test(message)) {
        return json({ error: "That username or email is already registered. Try signing in instead.", code: "AUTH_ACCOUNT_EXISTS" }, 409);
      }
      console.error("VoidCraft registration database insert failed", { name: error?.name || "Error" });
      return json({ error: "We couldn't save the account. Please try again.", code: "AUTH_REGISTER_SAVE_FAILED" }, 500);
    }

    stage = "create_session";
    let token;
    try {
      token = await createSession(env, id);
    } catch (error) {
      console.error("VoidCraft registration session creation failed", { name: error?.name || "Error" });
      return json({ error: "Your account was created, but the sign-in session could not start. Go to Sign in and log in with the account you just made.", code: "AUTH_SESSION_CREATE_FAILED" }, 503);
    }
    return json({ user: publicUser({ username, email, created_at: createdAt, last_login_at: null, role: "member" }), message: "Your VoidCraft account is ready." }, 201, {
      "Set-Cookie": sessionCookie(token)
    });
  } catch (error) {
    console.error("VoidCraft registration request failed", { stage, name: error?.name || "Error" });
    return json({ error: "The account service failed during " + stage.replace(/_/g, " ") + ". Please retry after refreshing.", code: "AUTH_REGISTER_INTERNAL_" + stage.toUpperCase() }, 500);
  }
}