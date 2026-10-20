import { allowAttempt, createSession, json, passwordHash, publicUser, randomHex, readJson, sameOrigin, sessionCookie } from "./_utils.js";

export async function onRequestPost({ request, env }) {
  if (!env?.VOIDCRAFT_AUTH) return json({ error: "Account service is temporarily unavailable." }, 503);
  if (!sameOrigin(request)) return json({ error: "Request origin could not be verified." }, 403);

  let limit;
  try {
    limit = await allowAttempt(request, env, "register");
  } catch {
    return json({ error: "Account service is temporarily unavailable." }, 503);
  }
  if (!limit.allowed) return json({ error: "Too many attempts. Please try again later." }, 429, { "Retry-After": String(limit.retryAfter) });

  const body = await readJson(request);
  if (!body) return json({ error: "Please send a valid registration form." }, 400);

  const username = typeof body.username === "string" ? body.username.trim() : "";
  const email = typeof body.email === "string" ? body.email.trim().toLowerCase() : "";
  const password = typeof body.password === "string" ? body.password : "";
  if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) {
    return json({ error: "Username must be 3–20 characters and use only letters, numbers, or underscores." }, 400);
  }
  if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return json({ error: "Enter a valid email address." }, 400);
  }
  if (password.length < 12 || password.length > 128) {
    return json({ error: "Choose a password between 12 and 128 characters." }, 400);
  }

  const salt = randomHex(16);
  const hash = await passwordHash(password, salt);
  const id = crypto.randomUUID();
  const createdAt = new Date().toISOString();
  try {
    await env.VOIDCRAFT_AUTH.prepare(
      "INSERT INTO users (id, username, email, password_salt, password_hash, created_at) VALUES (?, ?, ?, ?, ?, ?)"
    ).bind(id, username, email, salt, hash, createdAt).run();
  } catch (error) {
    const message = String(error?.message || "");
    if (/unique|constraint/i.test(message)) {
      return json({ error: "That username or email is already registered." }, 409);
    }
    return json({ error: "We couldn't create the account. Please try again." }, 500);
  }

  const user = { username, email, created_at: createdAt, last_login_at: null };
  const token = await createSession(env, id);
  return json({ user: publicUser(user), message: "Your VoidCraft account is ready." }, 201, {
    "Set-Cookie": sessionCookie(token)
  });
}