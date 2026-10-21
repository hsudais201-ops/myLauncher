const SESSION_DURATION_MS = 30 * 24 * 60 * 60 * 1000;
const PASSWORD_ITERATIONS = 210000;
const MAX_BODY_BYTES = 16 * 1024;
const textEncoder = new TextEncoder();

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", ...headers }
  });
}

function fail(status, message) {
  throw new HttpError(status, message);
}

function toBase64Url(bytes) {
  let raw = "";
  for (const byte of bytes) raw += String.fromCharCode(byte);
  return btoa(raw).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function fromBase64Url(value) {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  const raw = atob(padded);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i += 1) bytes[i] = raw.charCodeAt(i);
  return bytes;
}

async function sha256Hex(value) {
  const digest = await crypto.subtle.digest("SHA-256", textEncoder.encode(value));
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
}

async function hashPassword(password, salt) {
  const key = await crypto.subtle.importKey(
    "raw", textEncoder.encode(password), "PBKDF2", false, ["deriveBits"]
  );
  const bits = await crypto.subtle.deriveBits({
    name: "PBKDF2",
    salt,
    iterations: PASSWORD_ITERATIONS,
    hash: "SHA-256"
  }, key, 256);
  return toBase64Url(new Uint8Array(bits));
}

function constantTimeEqual(left, right) {
  if (typeof left !== "string" || typeof right !== "string" || left.length !== right.length) return false;
  let difference = 0;
  for (let i = 0; i < left.length; i += 1) difference |= left.charCodeAt(i) ^ right.charCodeAt(i);
  return difference === 0;
}

async function readJson(request) {
  const contentType = request.headers.get("Content-Type") || "";
  if (!contentType.toLowerCase().includes("application/json")) {
    fail(415, "Send a JSON request body.");
  }
  const declaredLength = Number(request.headers.get("Content-Length") || "0");
  if (Number.isFinite(declaredLength) && declaredLength > MAX_BODY_BYTES) {
    fail(413, "Request body is too large.");
  }
  if (!request.body) fail(400, "Expected a JSON request body.");

  const reader = request.body.getReader();
  const chunks = [];
  let total = 0;
  try {
    while (true) {
      const part = await reader.read();
      if (part.done) break;
      total += part.value.byteLength;
      if (total > MAX_BODY_BYTES) {
        await reader.cancel();
        fail(413, "Request body is too large.");
      }
      chunks.push(part.value);
    }
  } catch (error) {
    if (error instanceof HttpError) throw error;
    fail(400, "Could not read request body.");
  }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }

  let value;
  try {
    const raw = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
    value = JSON.parse(raw);
  } catch {
    fail(400, "Invalid JSON request body.");
  }
  if (!value || Array.isArray(value) || typeof value !== "object") {
    fail(400, "Expected a JSON object.");
  }
  return value;
}

function cleanText(value, maxLength) {
  return String(value ?? "").replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "").trim().slice(0, maxLength);
}

function safeUser(row) {
  return {
    id: Number(row.id),
    username: String(row.username),
    displayName: String(row.display_name || row.username),
    bio: String(row.bio || ""),
    role: row.role === "owner" || row.role === "moderator" ? row.role : "member",
    createdAt: new Date(Number(row.created_at)).toISOString()
  };
}

function getCookie(request, name) {
  const cookieHeader = request.headers.get("Cookie") || "";
  for (const part of cookieHeader.split(";")) {
    const separator = part.indexOf("=");
    if (separator < 0) continue;
    if (part.slice(0, separator).trim() === name) {
      return part.slice(separator + 1).trim();
    }
  }
  return null;
}

function bearerToken(request) {
  const value = request.headers.get("Authorization") || "";
  const match = /^Bearer\s+([A-Za-z0-9_-]+)$/i.exec(value);
  return match ? match[1] : null;
}

function isLauncherRoute(pathname) {
  return pathname.startsWith("/api/launcher/");
}

function verifyWriteOrigin(request, env, pathname) {
  const origin = request.headers.get("Origin");
  const appOrigin = String(env.APP_ORIGIN || "").replace(/\/+$/, "");
  if (origin && origin !== appOrigin) fail(403, "Origin is not allowed.");
  if (!origin && !bearerToken(request) && !isLauncherRoute(pathname)) {
    fail(403, "This request must come from the VoidCraft website.");
  }
}

async function enforceRateLimit(request, env, bucketLabel, identity, maxHits, windowMs) {
  // Trust X-VC-Client-IP only after the Worker-level proxy secret has been verified.
  // Cross-zone Workers subrequests may replace CF-Connecting-IP with a proxy IP.
  const ip = request.headers.get("X-VC-Client-IP") ||
    request.headers.get("CF-Connecting-IP") || "unknown";
  const subject = String(identity || ip);
  const bucket = await sha256Hex(bucketLabel + ":" + subject);
  const now = Date.now();
  const resetAt = now + windowMs;
  await env.DB.prepare("DELETE FROM rate_limits WHERE reset_at < ?")
    .bind(now - windowMs * 4).run();
  await env.DB.prepare(
    "INSERT INTO rate_limits (bucket, count, reset_at) VALUES (?, 1, ?) " +
    "ON CONFLICT(bucket) DO UPDATE SET " +
    "count = CASE WHEN rate_limits.reset_at <= ? THEN 1 ELSE rate_limits.count + 1 END, " +
    "reset_at = CASE WHEN rate_limits.reset_at <= ? THEN excluded.reset_at ELSE rate_limits.reset_at END"
  ).bind(bucket, resetAt, now, now).run();
  const record = await env.DB.prepare(
    "SELECT count, reset_at FROM rate_limits WHERE bucket = ?"
  ).bind(bucket).first();
  if (record && Number(record.count) > maxHits) {
    const retrySeconds = Math.max(1, Math.ceil((Number(record.reset_at) - now) / 1000));
    throw new HttpError(429, "Too many requests. Try again shortly.");
  }
}

async function createSession(env, userId) {
  const tokenBytes = crypto.getRandomValues(new Uint8Array(32));
  const token = toBase64Url(tokenBytes);
  const tokenHash = await sha256Hex(token);
  const now = Date.now();
  const expiresAt = now + SESSION_DURATION_MS;
  await env.DB.prepare(
    "INSERT INTO sessions (user_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?)"
  ).bind(userId, tokenHash, expiresAt, now).run();
  await env.DB.prepare("DELETE FROM sessions WHERE user_id = ? AND expires_at <= ?")
    .bind(userId, now).run();
  return { token, tokenHash, expiresAt };
}

async function startSession(request, env, user, status, launcherClient) {
  const session = await createSession(env, Number(user.id));
  const payload = {
    user: safeUser(user),
    expiresAt: new Date(session.expiresAt).toISOString()
  };
  const headers = {};
  if (launcherClient) {
    payload.accessToken = session.token;
    payload.tokenType = "Bearer";
  } else {
    const maxAge = Math.floor(SESSION_DURATION_MS / 1000);
    headers["Set-Cookie"] =
      "vc_session=" + session.token +
      "; Path=/; Max-Age=" + maxAge +
      "; Secure; HttpOnly; SameSite=Strict";
  }
  return json(payload, status, headers);
}

async function register(request, env, launcherClient) {
  await enforceRateLimit(request, env, "auth-register", null, 8, 15 * 60 * 1000);
  const body = await readJson(request);
  const email = cleanText(body.email, 254).toLowerCase();
  const username = cleanText(body.username, 20);
  const usernameNormalized = username.toLowerCase();
  const password = typeof body.password === "string" ? body.password : "";
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) {
    fail(400, "Enter a valid email address.");
  }
  if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) {
    fail(400, "Username must be 3–20 characters using letters, numbers, or underscores.");
  }
  if (password.length < 12 || password.length > 128) {
    fail(400, "Password must be between 12 and 128 characters.");
  }
  const existing = await env.DB.prepare(
    "SELECT id FROM users WHERE email_normalized = ? OR username_normalized = ? LIMIT 1"
  ).bind(email, usernameNormalized).first();
  if (existing) fail(409, "That email or username is already registered.");

  const salt = crypto.getRandomValues(new Uint8Array(16));
  const passwordHash = await hashPassword(password, salt);
  const now = Date.now();
  try {
    await env.DB.prepare(
      "INSERT INTO users (username, username_normalized, email, email_normalized, password_salt, password_hash, display_name, bio, role, created_at) " +
      "VALUES (?, ?, ?, ?, ?, ?, ?, '', 'member', ?)"
    ).bind(
      username, usernameNormalized, email, email, toBase64Url(salt), passwordHash,
      username, now
    ).run();
  } catch (error) {
    const message = String(error && error.message || "");
    if (/UNIQUE constraint failed/i.test(message)) fail(409, "That email or username is already registered.");
    throw error;
  }
  const user = await env.DB.prepare(
    "SELECT id, username, email, display_name, bio, role, created_at FROM users WHERE username_normalized = ?"
  ).bind(usernameNormalized).first();
  if (!user) throw new Error("New account could not be loaded.");
  return startSession(request, env, user, 201, launcherClient);
}

async function login(request, env, launcherClient) {
  await enforceRateLimit(request, env, "auth-login", null, 12, 15 * 60 * 1000);
  const body = await readJson(request);
  const identifier = cleanText(body.identifier ?? body.email ?? body.username, 254).toLowerCase();
  const password = typeof body.password === "string" ? body.password : "";
  if (!identifier || !password || password.length > 128) fail(400, "Enter your username/email and password.");
  const user = await env.DB.prepare(
    "SELECT id, username, email, password_salt, password_hash, display_name, bio, role, created_at " +
    "FROM users WHERE email_normalized = ? OR username_normalized = ? LIMIT 1"
  ).bind(identifier, identifier).first();
  const salt = user ? fromBase64Url(String(user.password_salt)) : new Uint8Array(16);
  const candidate = await hashPassword(password, salt);
  if (!user || !constantTimeEqual(candidate, String(user.password_hash))) {
    fail(401, "Invalid username/email or password.");
  }
  return startSession(request, env, user, 200, launcherClient);
}

async function requireUser(request, env) {
  const rawToken = bearerToken(request) || getCookie(request, "vc_session");
  if (!rawToken || !/^[A-Za-z0-9_-]{40,60}$/.test(rawToken)) fail(401, "Sign in to your VoidCraft account.");
  const tokenHash = await sha256Hex(rawToken);
  const now = Date.now();
  const user = await env.DB.prepare(
    "SELECT u.id, u.username, u.email, u.display_name, u.bio, u.role, u.created_at, s.id AS session_id " +
    "FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ? AND s.expires_at > ? LIMIT 1"
  ).bind(tokenHash, now).first();
  if (!user) fail(401, "Your session expired. Sign in again.");
  return user;
}

async function areFriends(env, firstId, secondId) {
  const low = Math.min(firstId, secondId);
  const high = Math.max(firstId, secondId);
  const row = await env.DB.prepare(
    "SELECT 1 AS linked FROM friendships WHERE user_id = ? AND friend_id = ?"
  ).bind(low, high).first();
  return Boolean(row);
}

async function isBlockedEitherWay(env, firstId, secondId) {
  const row = await env.DB.prepare(
    "SELECT 1 AS blocked FROM blocks WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?) LIMIT 1"
  ).bind(firstId, secondId, secondId, firstId).first();
  return Boolean(row);
}

async function requireOtherUser(env, userId, currentId) {
  if (!Number.isSafeInteger(userId) || userId < 1) fail(400, "Invalid player ID.");
  if (userId === currentId) fail(400, "You cannot perform this action on your own account.");
  const user = await env.DB.prepare(
    "SELECT id, username, display_name, bio, role, created_at FROM users WHERE id = ?"
  ).bind(userId).first();
  if (!user) fail(404, "Player not found.");
  return user;
}

async function requireFriendConversation(env, firstId, secondId) {
  if (!(await areFriends(env, firstId, secondId))) fail(403, "You can message only accepted friends.");
  if (await isBlockedEitherWay(env, firstId, secondId)) fail(403, "Messaging is unavailable for this player.");
}

async function route(request, env) {
  const url = new URL(request.url);
  const path = url.pathname;
  const method = request.method.toUpperCase();

  if (path === "/api/health" && method === "GET") {
    await env.DB.prepare("SELECT 1 AS ok").first();
    return json({ ok: true, service: "voidcraft-social-api" });
  }
  if (path === "/api/auth/register" && method === "POST") return register(request, env, false);
  if (path === "/api/auth/login" && method === "POST") return login(request, env, false);
  if (path === "/api/launcher/auth/register" && method === "POST") return register(request, env, true);
  if (path === "/api/launcher/auth/login" && method === "POST") return login(request, env, true);

  if (path === "/api/auth/logout" && method === "POST") {
    const user = await requireUser(request, env);
    const token = bearerToken(request) || getCookie(request, "vc_session");
    await env.DB.prepare("DELETE FROM sessions WHERE token_hash = ?").bind(await sha256Hex(token)).run();
    const native = Boolean(bearerToken(request));
    return json({ ok: true }, 200, native ? {} : {
      "Set-Cookie": "vc_session=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Strict"
    });
  }
  if (path === "/api/auth/me" && method === "GET") {
    const user = await requireUser(request, env);
    return json({ user: { ...safeUser(user), email: String(user.email) } });
  }

  const knownProtectedPath =
    path === "/api/auth/me" ||
    path === "/api/profile" ||
    path === "/api/players" ||
    path === "/api/friends" ||
    path === "/api/friends/requests" ||
    path === "/api/conversations" ||
    path === "/api/blocks" ||
    path === "/api/reports" ||
    path === "/api/moderation/reports" ||
    /^\/api\/moderation\/reports\/[0-9]+$/.test(path) ||
    /^\/api\/friends\/requests\/[0-9]+(?:\/accept)?$/.test(path) ||
    /^\/api\/friends\/[0-9]+$/.test(path) ||
    /^\/api\/conversations\/[0-9]+\/messages$/.test(path) ||
    /^\/api\/blocks\/[0-9]+$/.test(path);
  if (!knownProtectedPath) fail(404, "API endpoint not found.");

  const user = await requireUser(request, env);
  const userId = Number(user.id);

  if (path === "/api/moderation/reports" || /^\/api\/moderation\/reports\/[0-9]+$/.test(path)) {
    if (user.role !== "owner" && user.role !== "moderator") {
      fail(403, "Moderator permissions are required.");
    }
    if (path === "/api/moderation/reports" && method === "GET") {
      const requestedStatus = url.searchParams.get("status");
      const validStatuses = new Set(["open", "reviewing", "resolved", "dismissed"]);
      if (requestedStatus && !validStatuses.has(requestedStatus)) {
        fail(400, "Choose a valid report status.");
      }
      const requestedLimit = Number(url.searchParams.get("limit") || 50);
      const limit = Number.isFinite(requestedLimit)
        ? Math.min(100, Math.max(1, Math.trunc(requestedLimit)))
        : 50;
      const rows = requestedStatus
        ? await env.DB.prepare(
          "SELECT r.id, r.reporter_id, reporter.username AS reporter_username, " +
          "r.target_id, target.username AS target_username, r.category, r.details, r.status, r.created_at " +
          "FROM reports r JOIN users reporter ON reporter.id = r.reporter_id " +
          "JOIN users target ON target.id = r.target_id WHERE r.status = ? " +
          "ORDER BY r.created_at DESC, r.id DESC LIMIT ?"
        ).bind(requestedStatus, limit).all()
        : await env.DB.prepare(
          "SELECT r.id, r.reporter_id, reporter.username AS reporter_username, " +
          "r.target_id, target.username AS target_username, r.category, r.details, r.status, r.created_at " +
          "FROM reports r JOIN users reporter ON reporter.id = r.reporter_id " +
          "JOIN users target ON target.id = r.target_id " +
          "ORDER BY r.created_at DESC, r.id DESC LIMIT ?"
        ).bind(limit).all();
      return json({ reports: (rows.results || []).map(row => ({
        id: Number(row.id),
        reporterId: Number(row.reporter_id),
        reporterUsername: String(row.reporter_username),
        targetUserId: Number(row.target_id),
        targetUsername: String(row.target_username),
        category: String(row.category),
        details: String(row.details),
        status: String(row.status),
        createdAt: new Date(Number(row.created_at)).toISOString()
      })) });
    }
    const moderationMatch = /^\/api\/moderation\/reports\/([0-9]+)$/.exec(path);
    if (moderationMatch && method === "PATCH") {
      const reportId = Number(moderationMatch[1]);
      if (!Number.isSafeInteger(reportId) || reportId < 1) fail(400, "Invalid report ID.");
      await enforceRateLimit(request, env, "moderation-report-update", String(userId), 60, 60 * 1000);
      const body = await readJson(request);
      const status = cleanText(body.status, 20);
      if (!new Set(["open", "reviewing", "resolved", "dismissed"]).has(status)) {
        fail(400, "Choose a valid report status.");
      }
      const updated = await env.DB.prepare(
        "UPDATE reports SET status = ? WHERE id = ?"
      ).bind(status, reportId).run();
      if (!updated.meta || Number(updated.meta.changes || 0) < 1) fail(404, "Report not found.");
      return json({ ok: true, reportId, status });
    }
    fail(405, "Method not allowed for moderation reports.");
  }

  if (path === "/api/profile" && method === "GET") {
    return json({ user: { ...safeUser(user), email: String(user.email) } });
  }
  if (path === "/api/profile" && method === "PATCH") {
    await enforceRateLimit(request, env, "profile-update", String(userId), 20, 60 * 1000);
    const body = await readJson(request);
    const updates = [];
    const params = [];
    if (Object.prototype.hasOwnProperty.call(body, "displayName")) {
      const displayName = cleanText(body.displayName, 32);
      if (displayName.length < 1) fail(400, "Display name cannot be empty.");
      updates.push("display_name = ?");
      params.push(displayName);
    }
    if (Object.prototype.hasOwnProperty.call(body, "bio")) {
      const bio = cleanText(body.bio, 280);
      updates.push("bio = ?");
      params.push(bio);
    }
    if (updates.length === 0) fail(400, "No profile fields to update.");
    params.push(userId);
    await env.DB.prepare("UPDATE users SET " + updates.join(", ") + " WHERE id = ?").bind(...params).run();
    const updated = await env.DB.prepare(
      "SELECT id, username, email, display_name, bio, role, created_at FROM users WHERE id = ?"
    ).bind(userId).first();
    return json({ user: { ...safeUser(updated), email: String(updated.email) } });
  }

  if (path === "/api/players" && method === "GET") {
    await enforceRateLimit(request, env, "player-search", String(userId), 60, 60 * 1000);
    const query = cleanText(url.searchParams.get("q"), 20);
    if (!/^[A-Za-z0-9_]{3,20}$/.test(query)) fail(400, "Search with 3–20 letters, numbers, or underscores.");
    const players = await env.DB.prepare(
      "SELECT id, username, display_name, bio, role, created_at FROM users " +
      "WHERE username_normalized LIKE ? AND id <> ? " +
      "AND id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id = ?) " +
      "AND id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?) " +
      "ORDER BY username_normalized LIMIT 20"
    ).bind(query.toLowerCase() + "%", userId, userId, userId).all();
    return json({ players: (players.results || []).map(safeUser) });
  }

  if (path === "/api/friends" && method === "GET") {
    const friends = await env.DB.prepare(
      "SELECT u.id, u.username, u.display_name, u.bio, u.role, u.created_at, f.created_at AS friendship_created_at " +
      "FROM friendships f JOIN users u ON u.id = CASE WHEN f.user_id = ? THEN f.friend_id ELSE f.user_id END " +
      "WHERE f.user_id = ? OR f.friend_id = ? ORDER BY u.username_normalized LIMIT 200"
    ).bind(userId, userId, userId).all();
    return json({ friends: (friends.results || []).map(safeUser) });
  }

  if (path === "/api/friends/requests" && method === "GET") {
    const requests = await env.DB.prepare(
      "SELECT fr.id, fr.status, fr.created_at, fr.sender_id, fr.recipient_id, " +
      "u.username, u.display_name FROM friend_requests fr " +
      "JOIN users u ON u.id = CASE WHEN fr.sender_id = ? THEN fr.recipient_id ELSE fr.sender_id END " +
      "WHERE (fr.sender_id = ? OR fr.recipient_id = ?) AND fr.status = 'pending' " +
      "ORDER BY fr.created_at DESC LIMIT 100"
    ).bind(userId, userId, userId).all();
    return json({
      requests: (requests.results || []).map(row => ({
        id: Number(row.id),
        direction: Number(row.sender_id) === userId ? "outgoing" : "incoming",
        status: String(row.status),
        username: String(row.username),
        displayName: String(row.display_name || row.username),
        createdAt: new Date(Number(row.created_at)).toISOString()
      }))
    });
  }

  if (path === "/api/friends/requests" && method === "POST") {
    await enforceRateLimit(request, env, "friend-request", String(userId), 30, 60 * 60 * 1000);
    const body = await readJson(request);
    const username = cleanText(body.username, 20);
    if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) fail(400, "Enter a valid VoidCraft username.");
    const recipient = await env.DB.prepare(
      "SELECT id FROM users WHERE username_normalized = ?"
    ).bind(username.toLowerCase()).first();
    if (!recipient) fail(404, "Player not found.");
    const recipientId = Number(recipient.id);
    if (recipientId === userId) fail(400, "You cannot add yourself.");
    if (await isBlockedEitherWay(env, userId, recipientId)) fail(403, "A friend request cannot be sent to this player.");
    if (await areFriends(env, userId, recipientId)) fail(409, "You are already friends.");
    const existing = await env.DB.prepare(
      "SELECT id, sender_id, recipient_id FROM friend_requests WHERE status = 'pending' " +
      "AND ((sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?)) LIMIT 1"
    ).bind(userId, recipientId, recipientId, userId).first();
    if (existing) fail(409, Number(existing.sender_id) === recipientId ?
      "This player already sent you a request. Accept it from your requests." :
      "A friend request is already pending.");
    const now = Date.now();
    const result = await env.DB.prepare(
      "INSERT INTO friend_requests (sender_id, recipient_id, status, created_at) VALUES (?, ?, 'pending', ?)"
    ).bind(userId, recipientId, now).run();
    return json({ ok: true, requestId: Number(result.meta && result.meta.last_row_id || 0) }, 201);
  }

  const acceptMatch = /^\/api\/friends\/requests\/([0-9]+)\/accept$/.exec(path);
  if (acceptMatch && method === "POST") {
    const requestId = Number(acceptMatch[1]);
    const friendRequest = await env.DB.prepare(
      "SELECT id, sender_id, recipient_id FROM friend_requests WHERE id = ? AND recipient_id = ? AND status = 'pending'"
    ).bind(requestId, userId).first();
    if (!friendRequest) fail(404, "Incoming friend request not found.");
    const senderId = Number(friendRequest.sender_id);
    if (await isBlockedEitherWay(env, userId, senderId)) fail(403, "This request cannot be accepted.");
    const low = Math.min(userId, senderId);
    const high = Math.max(userId, senderId);
    await env.DB.batch([
      env.DB.prepare(
        "INSERT INTO friendships (user_id, friend_id, created_at) VALUES (?, ?, ?) ON CONFLICT(user_id, friend_id) DO NOTHING"
      ).bind(low, high, Date.now()),
      env.DB.prepare("UPDATE friend_requests SET status = 'accepted' WHERE id = ? AND recipient_id = ?")
        .bind(requestId, userId)
    ]);
    return json({ ok: true });
  }

  const requestMatch = /^\/api\/friends\/requests\/([0-9]+)$/.exec(path);
  if (requestMatch && method === "DELETE") {
    const result = await env.DB.prepare(
      "UPDATE friend_requests SET status = 'rejected' WHERE id = ? AND recipient_id = ? AND status = 'pending'"
    ).bind(Number(requestMatch[1]), userId).run();
    if (Number(result.meta && result.meta.changes || 0) === 0) fail(404, "Incoming friend request not found.");
    return json({ ok: true });
  }

  const friendMatch = /^\/api\/friends\/([0-9]+)$/.exec(path);
  if (friendMatch && method === "DELETE") {
    const friendId = Number(friendMatch[1]);
    if (!Number.isSafeInteger(friendId) || friendId < 1 || friendId === userId) fail(400, "Invalid player ID.");
    await env.DB.prepare(
      "DELETE FROM friendships WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)"
    ).bind(Math.min(userId, friendId), Math.max(userId, friendId), Math.min(userId, friendId), Math.max(userId, friendId)).run();
    return json({ ok: true });
  }

  if (path === "/api/conversations" && method === "GET") {
    const result = await env.DB.prepare(
      "WITH latest AS (" +
      " SELECT CASE WHEN sender_id = ? THEN recipient_id ELSE sender_id END AS peer_id, MAX(id) AS last_id " +
      " FROM messages WHERE sender_id = ? OR recipient_id = ? " +
      " GROUP BY CASE WHEN sender_id = ? THEN recipient_id ELSE sender_id END" +
      ") " +
      "SELECT u.id AS user_id, u.username, u.display_name, m.id AS message_id, m.body AS last_message, m.created_at AS last_message_at " +
      "FROM latest l JOIN messages m ON m.id = l.last_id JOIN users u ON u.id = l.peer_id " +
      "WHERE NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id = ? AND b.blocked_id = u.id) OR (b.blocker_id = u.id AND b.blocked_id = ?)) " +
      "AND EXISTS (SELECT 1 FROM friendships f WHERE f.user_id = CASE WHEN ? < u.id THEN ? ELSE u.id END " +
      "AND f.friend_id = CASE WHEN ? < u.id THEN u.id ELSE ? END) " +
      "ORDER BY m.id DESC LIMIT 50"
    ).bind(userId, userId, userId, userId, userId, userId, userId, userId, userId, userId).all();
    return json({ conversations: result.results || [] });
  }

  const messagesMatch = /^\/api\/conversations\/([0-9]+)\/messages$/.exec(path);
  if (messagesMatch) {
    const peerId = Number(messagesMatch[1]);
    await requireOtherUser(env, peerId, userId);
    await requireFriendConversation(env, userId, peerId);
    if (method === "GET") {
      await enforceRateLimit(request, env, "message-read", String(userId), 120, 60 * 1000);
      const rawBefore = url.searchParams.get("before");
      const before = rawBefore === null ? null : Number(rawBefore);
      if (before !== null && (!Number.isSafeInteger(before) || before < 1)) fail(400, "Invalid message cursor.");
      const rawLimit = Number(url.searchParams.get("limit") || "40");
      if (!Number.isSafeInteger(rawLimit) || rawLimit < 1 || rawLimit > 50) fail(400, "Message limit must be 1–50.");
      const messages = await env.DB.prepare(
        "SELECT m.id, m.sender_id, m.recipient_id, m.body, m.created_at, u.username AS sender_username " +
        "FROM messages m JOIN users u ON u.id = m.sender_id " +
        "WHERE ((m.sender_id = ? AND m.recipient_id = ?) OR (m.sender_id = ? AND m.recipient_id = ?)) " +
        "AND (? IS NULL OR m.id < ?) ORDER BY m.id DESC LIMIT ?"
      ).bind(userId, peerId, peerId, userId, before, before, rawLimit).all();
      const ordered = (messages.results || []).slice().reverse().map(row => ({
        id: Number(row.id), senderId: Number(row.sender_id), recipientId: Number(row.recipient_id),
        body: String(row.body), senderUsername: String(row.sender_username),
        createdAt: new Date(Number(row.created_at)).toISOString()
      }));
      return json({ messages: ordered });
    }
    if (method === "POST") {
      await enforceRateLimit(request, env, "message-send", String(userId), 30, 60 * 1000);
      const body = await readJson(request);
      const content = cleanText(body.content, 2000);
      if (!content) fail(400, "Message cannot be empty.");
      if (textEncoder.encode(content).byteLength > 6000) fail(400, "Message is too long.");
      const createdAt = Date.now();
      const inserted = await env.DB.prepare(
        "INSERT INTO messages (sender_id, recipient_id, body, created_at) VALUES (?, ?, ?, ?)"
      ).bind(userId, peerId, content, createdAt).run();
      return json({
        ok: true,
        message: {
          id: Number(inserted.meta && inserted.meta.last_row_id || 0),
          senderId: userId, recipientId: peerId, body: content,
          createdAt: new Date(createdAt).toISOString()
        }
      }, 201);
    }
  }

  if (path === "/api/blocks" && method === "GET") {
    const result = await env.DB.prepare(
      "SELECT u.id, u.username, u.display_name, b.created_at FROM blocks b " +
      "JOIN users u ON u.id = b.blocked_id WHERE b.blocker_id = ? ORDER BY b.created_at DESC LIMIT 200"
    ).bind(userId).all();
    return json({ blocks: result.results || [] });
  }

  const blockMatch = /^\/api\/blocks\/([0-9]+)$/.exec(path);
  if (blockMatch && method === "POST") {
    await enforceRateLimit(request, env, "block-player", String(userId), 30, 60 * 60 * 1000);
    const blockedId = Number(blockMatch[1]);
    await requireOtherUser(env, blockedId, userId);
    const now = Date.now();
    await env.DB.batch([
      env.DB.prepare(
        "INSERT INTO blocks (blocker_id, blocked_id, created_at) VALUES (?, ?, ?) ON CONFLICT(blocker_id, blocked_id) DO NOTHING"
      ).bind(userId, blockedId, now),
      env.DB.prepare(
        "DELETE FROM friendships WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)"
      ).bind(Math.min(userId, blockedId), Math.max(userId, blockedId), Math.min(userId, blockedId), Math.max(userId, blockedId)),
      env.DB.prepare(
        "UPDATE friend_requests SET status = 'rejected' WHERE status = 'pending' AND " +
        "((sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?))"
      ).bind(userId, blockedId, blockedId, userId)
    ]);
    return json({ ok: true });
  }
  if (blockMatch && method === "DELETE") {
    await env.DB.prepare("DELETE FROM blocks WHERE blocker_id = ? AND blocked_id = ?")
      .bind(userId, Number(blockMatch[1])).run();
    return json({ ok: true });
  }

  if (path === "/api/reports" && method === "POST") {
    await enforceRateLimit(request, env, "report-create", String(userId), 10, 60 * 60 * 1000);
    const body = await readJson(request);
    const targetId = Number(body.targetUserId);
    await requireOtherUser(env, targetId, userId);
    const category = cleanText(body.category, 40);
    const allowed = new Set(["harassment", "spam", "impersonation", "inappropriate-content", "other"]);
    if (!allowed.has(category)) fail(400, "Choose a valid report category.");
    const details = cleanText(body.details, 1000);
    const result = await env.DB.prepare(
      "INSERT INTO reports (reporter_id, target_id, category, details, status, created_at) " +
      "VALUES (?, ?, ?, ?, 'open', ?)"
    ).bind(userId, targetId, category, details, Date.now()).run();
    return json({ ok: true, reportId: Number(result.meta && result.meta.last_row_id || 0) }, 201);
  }

  fail(404, "API endpoint not found.");
}

function decorate(response, request, env) {
  const headers = new Headers(response.headers);
  headers.set("Cache-Control", "no-store");
  headers.set("X-Content-Type-Options", "nosniff");
  headers.set("X-Frame-Options", "DENY");
  headers.set("Referrer-Policy", "no-referrer");
  headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
  headers.set("Cross-Origin-Resource-Policy", "same-site");
  headers.set("Vary", "Origin");
  const origin = request.headers.get("Origin");
  const appOrigin = String(env.APP_ORIGIN || "").replace(/\/+$/, "");
  if (origin && origin === appOrigin) {
    headers.set("Access-Control-Allow-Origin", appOrigin);
    headers.set("Access-Control-Allow-Credentials", "true");
    headers.set("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS");
    headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-VoidCraft-Client");
    headers.set("Access-Control-Max-Age", "600");
  }
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
}

export default {
  async fetch(request, env) {
    let response;
    try {
      const configuredSecret = String(env.VOIDCRAFT_PROXY_SECRET || "");
      const suppliedSecret = request.headers.get("X-VC-Proxy-Secret") || "";
      if (configuredSecret.length < 32 || !constantTimeEqual(suppliedSecret, configuredSecret)) {
        fail(403, "Request must go through the configured VoidCraft website proxy.");
      }
      const url = new URL(request.url);
      const origin = request.headers.get("Origin");
      const appOrigin = String(env.APP_ORIGIN || "").replace(/\/+$/, "");
      if (origin && origin !== appOrigin) fail(403, "Origin is not allowed.");
      if (request.method.toUpperCase() === "OPTIONS") {
        response = new Response(null, { status: 204 });
      } else {
        if (["POST", "PATCH", "PUT", "DELETE"].includes(request.method.toUpperCase())) {
          verifyWriteOrigin(request, env, url.pathname);
        }
        response = await route(request, env);
      }
    } catch (error) {
      if (error instanceof HttpError) response = json({ error: error.message }, error.status);
      else response = json({ error: "An unexpected server error occurred." }, 500);
    }
    return decorate(response, request, env);
  }
};
