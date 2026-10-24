import { normalizeUsername } from "./validation.mjs";

const PASSWORD_ITERATIONS = 100000; // Cloudflare's per-operation PBKDF2 ceiling.
const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const MAX_JSON_BYTES = 16 * 1024;
const USERNAME_RE = /^[A-Za-z0-9_]{3,24}$/;
const VERSIONED_PASSWORD_PREFIX = "v2$";
const PASSWORD_HASH_PASSES = 6;
const REPORT_CATEGORIES = new Set(["harassment", "spam", "inappropriate", "impersonation", "other"]);

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer",
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET, POST, DELETE, OPTIONS",
      "access-control-allow-headers": "Authorization, Content-Type, X-Client-Version",
      "access-control-max-age": "86400",
    },
  });
}

function fail(status, message) {
  throw new HttpError(status, message);
}

async function readJson(request) {
  const length = Number(request.headers.get("content-length") || "0");
  if (length > MAX_JSON_BYTES) fail(413, "Request body is too large.");
  const text = await request.text();
  if (new TextEncoder().encode(text).byteLength > MAX_JSON_BYTES) fail(413, "Request body is too large.");
  try {
    const value = JSON.parse(text);
    if (!value || typeof value !== "object" || Array.isArray(value)) fail(400, "A JSON object is required.");
    return value;
  } catch (error) {
    if (error instanceof HttpError) throw error;
    fail(400, "Invalid JSON.");
  }
}

function cleanString(value, maxLength) {
  return typeof value === "string" ? value.trim().slice(0, maxLength) : "";
}

function bytesToHex(bytes) {
  return Array.from(bytes, byte => byte.toString(16).padStart(2, "0")).join("");
}

function hexToBytes(value) {
  const bytes = new Uint8Array(value.length / 2);
  for (let i = 0; i < bytes.length; i++) bytes[i] = parseInt(value.slice(i * 2, i * 2 + 2), 16);
  return bytes;
}

function randomHex(byteLength = 32) {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return bytesToHex(bytes);
}

async function sha256(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return bytesToHex(new Uint8Array(digest));
}

async function hashPassword(password, saltHex) {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(password),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const result = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt: hexToBytes(saltHex), iterations: PASSWORD_ITERATIONS },
    key,
    256,
  );
  return bytesToHex(new Uint8Array(result));
}

async function hashPasswordV2(password, saltHex) {
  let input = new TextEncoder().encode(password);
  for (let pass = 0; pass < PASSWORD_HASH_PASSES; pass++) {
    const label = new TextEncoder().encode(saltHex + ":voidcraft-password-chain-v2:" + pass);
    const material = await crypto.subtle.importKey("raw", input, "PBKDF2", false, ["deriveBits"]);
    input = new Uint8Array(await crypto.subtle.deriveBits({
      name: "PBKDF2",
      hash: "SHA-256",
      salt: label,
      iterations: PASSWORD_ITERATIONS,
    }, material, 256));
  }
  return bytesToHex(input);
}

function constantTimeEqual(left, right) {
  if (typeof left !== "string" || typeof right !== "string" || left.length !== right.length) return false;
  let difference = 0;
  for (let i = 0; i < left.length; i++) difference |= left.charCodeAt(i) ^ right.charCodeAt(i);
  return difference === 0;
}

function requireUsername(value) {
  const username = normalizeUsername(value);
  if (!username) fail(400, "Username must be 3–24 characters using letters, numbers, or underscore.");
  return username;
}

function requirePassword(value) {
  if (typeof value !== "string" || value.length < 12 || value.length > 128) {
    fail(400, "Password must be 12–128 characters.");
  }
  return value;
}

async function takeRateLimit(db, bucket, windowMs, maximum) {
  const currentWindow = Math.floor(Date.now() / windowMs) * windowMs;
  await db.prepare(
    "INSERT INTO auth_rate_limits (bucket_key, window_start, attempts) VALUES (?, ?, 1) " +
    "ON CONFLICT(bucket_key) DO UPDATE SET " +
    "attempts = CASE WHEN auth_rate_limits.window_start <> excluded.window_start THEN 1 ELSE auth_rate_limits.attempts + 1 END, " +
    "window_start = excluded.window_start",
  ).bind(bucket, currentWindow).run();
  const row = await db.prepare("SELECT attempts FROM auth_rate_limits WHERE bucket_key = ?").bind(bucket).first();
  if ((row?.attempts || 0) > maximum) fail(429, "Too many requests. Please wait and try again.");
}

async function limitByIp(db, request, action, windowMs, maximum) {
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";
  await takeRateLimit(db, action + ":" + await sha256(ip), windowMs, maximum);
}

async function issueSession(db, userId) {
  const token = randomHex(32);
  const now = Date.now();
  await db.prepare("DELETE FROM sessions WHERE expires_at <= ?").bind(now).run();
  await db.prepare(
    "INSERT INTO sessions (id, user_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
  ).bind(crypto.randomUUID(), userId, await sha256(token), now, now + SESSION_TTL_MS).run();
  return token;
}

async function authenticate(request, db) {
  const header = request.headers.get("authorization") || "";
  const match = /^Bearer ([a-f0-9]{64})$/i.exec(header);
  if (!match) fail(401, "Sign in is required.");
  const tokenHash = await sha256(match[1].toLowerCase());
  const row = await db.prepare(
    "SELECT u.id, u.username, s.id AS session_id FROM sessions s " +
    "JOIN users u ON u.id = s.user_id WHERE s.token_hash = ? AND s.expires_at > ?",
  ).bind(tokenHash, Date.now()).first();
  if (!row) fail(401, "Session expired. Please sign in again.");
  return row;
}

async function isBlocked(db, firstId, secondId) {
  const row = await db.prepare(
    "SELECT 1 AS found FROM social_blocks WHERE (blocker_id = ? AND blocked_id = ?) " +
    "OR (blocker_id = ? AND blocked_id = ?) LIMIT 1",
  ).bind(firstId, secondId, secondId, firstId).first();
  return Boolean(row);
}

async function areFriends(db, firstId, secondId) {
  const [low, high] = firstId < secondId ? [firstId, secondId] : [secondId, firstId];
  return Boolean(await db.prepare(
    "SELECT 1 AS found FROM social_friendships WHERE user_low = ? AND user_high = ?",
  ).bind(low, high).first());
}

async function register(request, env) {
  await limitByIp(env.DB, request, "register", 60 * 60 * 1000, 8);
  const body = await readJson(request);
  const username = requireUsername(body.username);
  const password = requirePassword(body.password);
  const existing = await env.DB.prepare("SELECT id FROM users WHERE username = ? COLLATE NOCASE").bind(username).first();
  if (existing) fail(409, "That username is unavailable.");
  const userId = crypto.randomUUID();
  const salt = randomHex(16);
  const passwordHash = VERSIONED_PASSWORD_PREFIX + await hashPasswordV2(password, salt);
  // This beta does not collect email; the schema uses a reserved, non-deliverable domain internally.
  const internalEmail = username.toLowerCase() + "@users.voidcraft.invalid";
  try {
    await env.DB.prepare(
      "INSERT INTO users (id, username, email, password_salt, password_hash, created_at, role) " +
      "VALUES (?, ?, ?, ?, ?, ?, 'member')",
    ).bind(userId, username, internalEmail, salt, passwordHash, new Date().toISOString()).run();
  } catch {
    fail(409, "That username is unavailable.");
  }
  const token = await issueSession(env.DB, userId);
  return json({ token, user: { id: userId, username }, expiresInSeconds: Math.floor(SESSION_TTL_MS / 1000) }, 201);
}

async function login(request, env) {
  await limitByIp(env.DB, request, "login", 15 * 60 * 1000, 10);
  const body = await readJson(request);
  const username = normalizeUsername(body.username);
  const password = typeof body.password === "string" ? body.password : "";
  if (!username || password.length > 128 || password.length === 0) {
    fail(401, "Invalid username or password.");
  }
  const user = await env.DB.prepare(
    "SELECT id, username, password_salt, password_hash FROM users WHERE username = ? COLLATE NOCASE",
  ).bind(username).first();
  if (!user) {
    await hashPasswordV2(password, "00000000000000000000000000000000");
    fail(401, "Invalid username or password.");
  }
  const usesStrongHash = typeof user.password_hash === "string" && user.password_hash.startsWith(VERSIONED_PASSWORD_PREFIX);
  const expectedHash = usesStrongHash ? user.password_hash.slice(VERSIONED_PASSWORD_PREFIX.length) : user.password_hash;
  const actual = usesStrongHash
    ? await hashPasswordV2(password, user.password_salt)
    : await hashPassword(password, user.password_salt);
  if (!constantTimeEqual(actual, expectedHash)) fail(401, "Invalid username or password.");
  const loggedInAt = new Date().toISOString();
  if (usesStrongHash) {
    await env.DB.prepare("UPDATE users SET last_login_at = ? WHERE id = ?").bind(loggedInAt, user.id).run();
  } else {
    // Upgrade existing single-pass hashes only after successful verification.
    const upgradedSalt = randomHex(16);
    const upgradedHash = VERSIONED_PASSWORD_PREFIX + await hashPasswordV2(password, upgradedSalt);
    await env.DB.prepare("UPDATE users SET password_salt = ?, password_hash = ?, last_login_at = ? WHERE id = ?")
      .bind(upgradedSalt, upgradedHash, loggedInAt, user.id).run();
  }
  const token = await issueSession(env.DB, user.id);
  return json({ token, user: { id: user.id, username: user.username }, expiresInSeconds: Math.floor(SESSION_TTL_MS / 1000) });
}

async function getFriends(user, env) {
  const friends = await env.DB.prepare(
    "SELECT u.id, u.username, f.created_at FROM social_friendships f " +
    "JOIN users u ON u.id = CASE WHEN f.user_low = ? THEN f.user_high ELSE f.user_low END " +
    "WHERE f.user_low = ? OR f.user_high = ? ORDER BY u.username COLLATE NOCASE",
  ).bind(user.id, user.id, user.id).all();
  const incoming = await env.DB.prepare(
    "SELECT r.id, u.username, r.created_at FROM social_friend_requests r " +
    "JOIN users u ON u.id = r.sender_id WHERE r.recipient_id = ? AND r.status = 'pending' ORDER BY r.created_at DESC LIMIT 100",
  ).bind(user.id).all();
  const outgoing = await env.DB.prepare(
    "SELECT r.id, u.username, r.created_at FROM social_friend_requests r " +
    "JOIN users u ON u.id = r.recipient_id WHERE r.sender_id = ? AND r.status = 'pending' ORDER BY r.created_at DESC LIMIT 100",
  ).bind(user.id).all();
  const blocked = await env.DB.prepare(
    "SELECT u.username FROM social_blocks b JOIN users u ON u.id = b.blocked_id WHERE b.blocker_id = ? ORDER BY u.username COLLATE NOCASE",
  ).bind(user.id).all();
  return json({
    friends: (friends.results || []).map(row => ({ username: row.username, since: row.created_at })),
    incoming: incoming.results || [],
    outgoing: outgoing.results || [],
    blocked: (blocked.results || []).map(row => row.username),
  });
}

async function createFriendRequest(request, user, env) {
  await takeRateLimit(env.DB, "friend-request:" + user.id, 60 * 60 * 1000, 20);
  const body = await readJson(request);
  const username = requireUsername(body.username);
  const target = await env.DB.prepare("SELECT id, username FROM users WHERE username = ? COLLATE NOCASE").bind(username).first();
  if (!target) fail(404, "No user found with that username.");
  if (target.id === user.id) fail(400, "You cannot add yourself.");
  if (await isBlocked(env.DB, user.id, target.id)) fail(403, "This contact cannot receive a request.");
  if (await areFriends(env.DB, user.id, target.id)) fail(409, "You are already friends.");
  const pending = await env.DB.prepare(
    "SELECT id, sender_id FROM social_friend_requests WHERE status = 'pending' " +
    "AND ((sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?)) LIMIT 1",
  ).bind(user.id, target.id, target.id, user.id).first();
  if (pending) fail(409, pending.sender_id === target.id ? "This user has already sent you a request." : "A request is already pending.");
  await env.DB.prepare(
    "INSERT INTO social_friend_requests (id, sender_id, recipient_id, status, created_at) VALUES (?, ?, ?, 'pending', ?)",
  ).bind(crypto.randomUUID(), user.id, target.id, Date.now()).run();
  return json({ ok: true, message: "Friend request sent." }, 201);
}

async function respondFriendRequest(request, user, env, requestId) {
  const body = await readJson(request);
  const action = body.action;
  if (!["accept", "reject", "cancel"].includes(action)) fail(400, "Action must be accept, reject, or cancel.");
  const condition = action === "cancel" ? "sender_id = ?" : "recipient_id = ?";
  const row = await env.DB.prepare(
    "SELECT id, sender_id, recipient_id FROM social_friend_requests WHERE id = ? AND status = 'pending' AND " + condition,
  ).bind(requestId, user.id).first();
  if (!row) fail(404, "Pending friend request not found.");
  if (action === "accept" && await isBlocked(env.DB, user.id, row.sender_id)) fail(403, "Unblock this user before accepting the request.");
  if (action === "accept") {
    const [low, high] = row.sender_id < row.recipient_id ? [row.sender_id, row.recipient_id] : [row.recipient_id, row.sender_id];
    await env.DB.batch([
      env.DB.prepare("INSERT OR IGNORE INTO social_friendships (user_low, user_high, created_at) VALUES (?, ?, ?)")
        .bind(low, high, Date.now()),
      env.DB.prepare("UPDATE social_friend_requests SET status = 'accepted' WHERE id = ? AND status = 'pending'").bind(row.id),
    ]);
  } else {
    await env.DB.prepare("UPDATE social_friend_requests SET status = ? WHERE id = ? AND status = 'pending'")
      .bind(action === "reject" ? "rejected" : "cancelled", row.id).run();
  }
  return json({ ok: true });
}

async function removeFriend(request, user, env) {
  const body = await readJson(request);
  const username = requireUsername(body.username);
  const target = await env.DB.prepare("SELECT id FROM users WHERE username = ? COLLATE NOCASE").bind(username).first();
  if (!target || target.id === user.id) fail(404, "Friend not found.");
  const [low, high] = user.id < target.id ? [user.id, target.id] : [target.id, user.id];
  await env.DB.batch([
    env.DB.prepare("DELETE FROM social_friendships WHERE user_low = ? AND user_high = ?").bind(low, high),
    env.DB.prepare("UPDATE social_friend_requests SET status = 'cancelled' WHERE status = 'pending' AND ((sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?))")
      .bind(user.id, target.id, target.id, user.id),
  ]);
  return json({ ok: true });
}

async function addBlock(request, user, env) {
  const body = await readJson(request);
  const username = requireUsername(body.username);
  const target = await env.DB.prepare("SELECT id FROM users WHERE username = ? COLLATE NOCASE").bind(username).first();
  if (!target || target.id === user.id) fail(404, "User not found.");
  const [low, high] = user.id < target.id ? [user.id, target.id] : [target.id, user.id];
  await env.DB.batch([
    env.DB.prepare("INSERT OR IGNORE INTO social_blocks (blocker_id, blocked_id, created_at) VALUES (?, ?, ?)")
      .bind(user.id, target.id, Date.now()),
    env.DB.prepare("DELETE FROM social_friendships WHERE user_low = ? AND user_high = ?").bind(low, high),
    env.DB.prepare("UPDATE social_friend_requests SET status = 'cancelled' WHERE status = 'pending' AND ((sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?))")
      .bind(user.id, target.id, target.id, user.id),
  ]);
  return json({ ok: true });
}

async function unblock(username, user, env) {
  const target = await env.DB.prepare("SELECT id FROM users WHERE username = ? COLLATE NOCASE").bind(username).first();
  if (!target) fail(404, "Blocked user not found.");
  await env.DB.prepare("DELETE FROM social_blocks WHERE blocker_id = ? AND blocked_id = ?").bind(user.id, target.id).run();
  return json({ ok: true });
}

async function listMessages(url, user, env) {
  const username = requireUsername(url.searchParams.get("with"));
  const after = Number(url.searchParams.get("after") || "0");
  if (!Number.isSafeInteger(after) || after < 0) fail(400, "Invalid message cursor.");
  const target = await env.DB.prepare("SELECT id, username FROM users WHERE username = ? COLLATE NOCASE").bind(username).first();
  if (!target || target.id === user.id) fail(404, "Friend not found.");
  if (await isBlocked(env.DB, user.id, target.id) || !(await areFriends(env.DB, user.id, target.id))) {
    fail(403, "Messages are only available between friends who have not blocked each other.");
  }
  const rows = await env.DB.prepare(
    "SELECT m.id, sender.username AS sender, recipient.username AS recipient, m.body, m.created_at " +
    "FROM social_messages m JOIN users sender ON sender.id = m.sender_id " +
    "JOIN users recipient ON recipient.id = m.recipient_id " +
    "WHERE ((m.sender_id = ? AND m.recipient_id = ?) OR (m.sender_id = ? AND m.recipient_id = ?)) " +
    "AND m.created_at > ? ORDER BY m.created_at DESC LIMIT 50",
  ).bind(user.id, target.id, target.id, user.id, after).all();
  return json({ messages: (rows.results || []).reverse(), nextAfter: (rows.results || []).reduce((max, row) => Math.max(max, row.created_at), after) });
}

async function sendMessage(request, user, env) {
  await takeRateLimit(env.DB, "messages:" + user.id, 60 * 1000, 40);
  const body = await readJson(request);
  const username = requireUsername(body.username);
  const text = typeof body.text === "string" ? body.text.trim() : "";
  if (text.length < 1 || text.length > 2000) fail(400, "Message must be 1–2000 characters.");
  const target = await env.DB.prepare("SELECT id, username FROM users WHERE username = ? COLLATE NOCASE").bind(username).first();
  if (!target || target.id === user.id) fail(404, "Friend not found.");
  if (await isBlocked(env.DB, user.id, target.id) || !(await areFriends(env.DB, user.id, target.id))) {
    fail(403, "You can only message a friend who has not blocked you.");
  }
  const id = crypto.randomUUID();
  const createdAt = Date.now();
  await env.DB.prepare(
    "INSERT INTO social_messages (id, sender_id, recipient_id, body, created_at) VALUES (?, ?, ?, ?, ?)",
  ).bind(id, user.id, target.id, text, createdAt).run();
  return json({ message: { id, sender: user.username, recipient: target.username, body: text, created_at: createdAt } }, 201);
}

async function reportUser(request, user, env) {
  await takeRateLimit(env.DB, "reports:" + user.id, 60 * 60 * 1000, 10);
  const body = await readJson(request);
  const username = requireUsername(body.username);
  const category = cleanString(body.category, 32);
  const details = cleanString(body.details, 1000);
  if (!REPORT_CATEGORIES.has(category)) fail(400, "Invalid report category.");
  const target = await env.DB.prepare("SELECT id FROM users WHERE username = ? COLLATE NOCASE").bind(username).first();
  if (!target || target.id === user.id) fail(404, "User not found.");
  await env.DB.prepare(
    "INSERT INTO social_reports (id, reporter_id, target_user_id, category, details, status, created_at) VALUES (?, ?, ?, ?, ?, 'open', ?)",
  ).bind(crypto.randomUUID(), user.id, target.id, category, details, Date.now()).run();
  return json({ ok: true }, 201);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return json({ ok: true });
    if (url.pathname === "/" || url.pathname === "/api/health") {
      return request.method === "GET"
        ? json({ service: "Voidcraft Social Hub API", apiVersion: 1, status: "ok", environment: env.APP_ENV || "unknown" })
        : json({ error: "Method not allowed." }, 405);
    }

    try {
      if (request.method === "POST" && url.pathname === "/api/register") return await register(request, env);
      if (request.method === "POST" && url.pathname === "/api/login") return await login(request, env);

      const user = await authenticate(request, env.DB);

      if (request.method === "GET" && url.pathname === "/api/me") {
        return json({ user: { id: user.id, username: user.username } });
      }
      if (request.method === "POST" && url.pathname === "/api/logout") {
        const token = (request.headers.get("authorization") || "").slice(7).toLowerCase();
        await env.DB.prepare("DELETE FROM sessions WHERE token_hash = ?").bind(await sha256(token)).run();
        return json({ ok: true });
      }
      if (request.method === "GET" && url.pathname === "/api/friends") return await getFriends(user, env);
      if (request.method === "POST" && url.pathname === "/api/friends/requests") return await createFriendRequest(request, user, env);
      const requestMatch = url.pathname.match(/^\/api\/friends\/requests\/([0-9a-f-]{36})\/respond$/i);
      if (request.method === "POST" && requestMatch) return await respondFriendRequest(request, user, env, requestMatch[1]);
      if (request.method === "POST" && url.pathname === "/api/friends/remove") return await removeFriend(request, user, env);
      if (request.method === "POST" && url.pathname === "/api/blocks") return await addBlock(request, user, env);
      const unblockMatch = url.pathname.match(/^\/api\/blocks\/([A-Za-z0-9_]{3,24})$/);
      if (request.method === "DELETE" && unblockMatch) return await unblock(decodeURIComponent(unblockMatch[1]), user, env);
      if (request.method === "GET" && url.pathname === "/api/messages") return await listMessages(url, user, env);
      if (request.method === "POST" && url.pathname === "/api/messages") return await sendMessage(request, user, env);
      if (request.method === "POST" && url.pathname === "/api/reports") return await reportUser(request, user, env);
      return json({ error: "Endpoint not found." }, 404);
    } catch (error) {
      if (error instanceof HttpError) return json({ error: error.message }, error.status);
      console.error("Social Hub request failed.", {
        path: url.pathname,
        errorName: error instanceof Error ? error.name : "UnknownError",
        errorMessage: error instanceof Error ? error.message : String(error),
      });
      return json({ error: "Server error. Please try again later." }, 500);
    }
  },
};
