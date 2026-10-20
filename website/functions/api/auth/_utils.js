const COOKIE_NAME = "__Host-vc_session";
const SESSION_TTL_SECONDS = 60 * 60 * 24 * 7;
const RATE_WINDOW_MS = 15 * 60 * 1000;
const RATE_LIMIT = 10;
const PBKDF2_ITERATIONS = 600000;

export function json(data, status = 200, extraHeaders = {}) {
  const headers = new Headers({
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store, max-age=0",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
    ...extraHeaders
  });
  return new Response(JSON.stringify(data), { status, headers });
}

export function sameOrigin(request) {
  const expected = new URL(request.url).origin;
  const origin = request.headers.get("Origin");
  if (origin) return origin === expected;
  const fetchSite = request.headers.get("Sec-Fetch-Site");
  if (fetchSite) return fetchSite === "same-origin";
  const referer = request.headers.get("Referer");
  try { return Boolean(referer) && new URL(referer).origin === expected; }
  catch { return false; }
}

export async function readJson(request) {
  const type = request.headers.get("Content-Type") || "";
  if (!type.toLowerCase().includes("application/json")) return null;
  const length = Number(request.headers.get("Content-Length") || 0);
  if (length > 8192) return null;
  try {
    const raw = await request.text();
    if (new TextEncoder().encode(raw).byteLength > 8192) return null;
    const body = JSON.parse(raw);
    if (!body || typeof body !== "object" || Array.isArray(body)) return null;
    return body;
  } catch { return null; }
}

function hex(bytes) {
  return Array.from(new Uint8Array(bytes), (b) => b.toString(16).padStart(2, "0")).join("");
}

function fromHex(value) {
  const bytes = new Uint8Array(value.length / 2);
  for (let i = 0; i < bytes.length; i++) bytes[i] = parseInt(value.slice(i * 2, i * 2 + 2), 16);
  return bytes;
}

export function randomHex(byteLength = 32) {
  return hex(crypto.getRandomValues(new Uint8Array(byteLength)));
}

export async function sha256Hex(value) {
  return hex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)));
}

export async function passwordHash(password, saltHex) {
  const material = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({
    name: "PBKDF2", hash: "SHA-256", salt: fromHex(saltHex), iterations: PBKDF2_ITERATIONS
  }, material, 256);
  return hex(bits);
}

export function constantTimeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

export async function allowAttempt(request, env, action) {
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";
  const bucket = await sha256Hex(action + "|" + ip);
  const now = Date.now();
  await env.VOIDCRAFT_AUTH.prepare(
    "INSERT INTO auth_rate_limits (bucket_key, window_start, attempts) VALUES (?, ?, 1) " +
    "ON CONFLICT(bucket_key) DO UPDATE SET " +
    "attempts = CASE WHEN ? - auth_rate_limits.window_start >= ? THEN 1 ELSE auth_rate_limits.attempts + 1 END, " +
    "window_start = CASE WHEN ? - auth_rate_limits.window_start >= ? THEN ? ELSE auth_rate_limits.window_start END"
  ).bind(bucket, now, now, RATE_WINDOW_MS, now, RATE_WINDOW_MS, now).run();
  const row = await env.VOIDCRAFT_AUTH.prepare(
    "SELECT attempts, window_start FROM auth_rate_limits WHERE bucket_key = ?"
  ).bind(bucket).first();
  if (now % 97 === 0) {
    await env.VOIDCRAFT_AUTH.prepare("DELETE FROM auth_rate_limits WHERE window_start < ?")
      .bind(now - 24 * 60 * 60 * 1000).run();
  }
  return {
    allowed: Number(row?.attempts || 0) <= RATE_LIMIT,
    retryAfter: Math.max(1, Math.ceil((Number(row?.window_start || now) + RATE_WINDOW_MS - now) / 1000))
  };
}

export function sessionCookie(token) {
  return COOKIE_NAME + "=" + token + "; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=" + SESSION_TTL_SECONDS;
}
export function clearSessionCookie() {
  return COOKIE_NAME + "=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0";
}
export function getSessionToken(request) {
  const cookie = request.headers.get("Cookie") || "";
  const match = cookie.match(/(?:^|;\s*)__Host-vc_session=([a-f0-9]{64})(?:;|$)/i);
  return match ? match[1] : null;
}

export async function createSession(env, userId) {
  const token = randomHex(32);
  const now = Math.floor(Date.now() / 1000);
  await env.VOIDCRAFT_AUTH.prepare("DELETE FROM sessions WHERE expires_at <= ?").bind(now).run();
  await env.VOIDCRAFT_AUTH.prepare(
    "INSERT INTO sessions (id, user_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?)"
  ).bind(crypto.randomUUID(), userId, await sha256Hex(token), now, now + SESSION_TTL_SECONDS).run();
  return token;
}

export async function currentUser(request, env) {
  const token = getSessionToken(request);
  if (!token || !env?.VOIDCRAFT_AUTH) return null;
  const now = Math.floor(Date.now() / 1000);
  return await env.VOIDCRAFT_AUTH.prepare(
    "SELECT u.id, u.username, u.email, u.created_at, u.last_login_at " +
    "FROM sessions s JOIN users u ON u.id = s.user_id " +
    "WHERE s.token_hash = ? AND s.expires_at > ? LIMIT 1"
  ).bind(await sha256Hex(token), now).first();
}
export async function deleteSession(request, env) {
  const token = getSessionToken(request);
  if (!token || !env?.VOIDCRAFT_AUTH) return;
  await env.VOIDCRAFT_AUTH.prepare("DELETE FROM sessions WHERE token_hash = ?")
    .bind(await sha256Hex(token)).run();
}
export function publicUser(user) {
  return { username: user.username, email: user.email, createdAt: user.created_at, lastLoginAt: user.last_login_at || null };
}
