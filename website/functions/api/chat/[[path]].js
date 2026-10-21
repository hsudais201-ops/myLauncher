import { currentUser, json, readJson, sameOrigin } from "../auth/_utils.js";

class ChatError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function fail(status, message) {
  throw new ChatError(status, message);
}

function cleanText(value, limit) {
  return String(value ?? "")
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "")
    .trim()
    .slice(0, limit);
}

function safeUser(user) {
  return {
    id: String(user.id),
    username: String(user.username || ""),
    role: user.role === "owner" ? "owner" : "member",
    createdAt: user.created_at || null
  };
}

function sortedPair(first, second) {
  return first < second ? [first, second] : [second, first];
}

async function rateLimit(env, action, userId, maximum, windowMs) {
  const now = Date.now();
  const start = Math.floor(now / windowMs) * windowMs;
  const key = action + ":" + String(userId);
  await env.VOIDCRAFT_AUTH.prepare(
    "INSERT INTO social_rate_limits (bucket_key, window_start, attempts) VALUES (?, ?, 1) " +
    "ON CONFLICT(bucket_key) DO UPDATE SET " +
    "attempts = CASE WHEN social_rate_limits.window_start = excluded.window_start " +
    "THEN social_rate_limits.attempts + 1 ELSE 1 END, window_start = excluded.window_start"
  ).bind(key, start).run();
  const row = await env.VOIDCRAFT_AUTH.prepare(
    "SELECT attempts, window_start FROM social_rate_limits WHERE bucket_key = ?"
  ).bind(key).first();
  if (Number(row?.attempts || 0) > maximum) fail(429, "Too many requests. Please try again shortly.");
  // Keep the rate-limit table bounded without affecting active windows.
  await env.VOIDCRAFT_AUTH.prepare(
    "DELETE FROM social_rate_limits WHERE window_start < ?"
  ).bind(now - 24 * 60 * 60 * 1000).run();
}

async function requireOtherUser(env, targetId, currentId) {
  const id = String(targetId || "");
  if (id.length < 10 || id.length > 64 || id === currentId) {
    fail(400, "Choose a valid other player.");
  }
  const user = await env.VOIDCRAFT_AUTH.prepare(
    "SELECT id, username, role, created_at FROM users WHERE id = ? LIMIT 1"
  ).bind(id).first();
  if (!user) fail(404, "Player not found.");
  return user;
}

async function areFriends(env, first, second) {
  const pair = sortedPair(first, second);
  const row = await env.VOIDCRAFT_AUTH.prepare(
    "SELECT 1 AS found FROM social_friendships WHERE user_low_id = ? AND user_high_id = ? LIMIT 1"
  ).bind(pair[0], pair[1]).first();
  return Boolean(row);
}

async function isBlocked(env, first, second) {
  const row = await env.VOIDCRAFT_AUTH.prepare(
    "SELECT 1 AS found FROM social_blocks WHERE " +
    "(blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?) LIMIT 1"
  ).bind(first, second, second, first).first();
  return Boolean(row);
}

async function requireFriendConversation(env, first, second) {
  if (!(await areFriends(env, first, second))) fail(403, "You can chat only with accepted friends.");
  if (await isBlocked(env, first, second)) fail(403, "Chat is unavailable for this player.");
}

async function route(request, env, user) {
  const url = new URL(request.url);
  const path = url.pathname.slice("/api/chat".length) || "/";
  const method = request.method.toUpperCase();
  const userId = String(user.id);

  if (path === "/me" && method === "GET") {
    return json({ user: safeUser(user) });
  }

  if (path === "/players" && method === "GET") {
    await rateLimit(env, "player-search", userId, 60, 60 * 1000);
    const query = cleanText(url.searchParams.get("q"), 20);
    if (!/^[A-Za-z0-9_]{3,20}$/.test(query)) {
      fail(400, "Search with 3–20 letters, numbers, or underscores.");
    }
    const result = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT u.id, u.username, u.role, u.created_at FROM users u " +
      "WHERE u.username LIKE ? COLLATE NOCASE AND u.id <> ? " +
      "AND NOT EXISTS (SELECT 1 FROM social_blocks b WHERE " +
      "(b.blocker_id = ? AND b.blocked_id = u.id) OR (b.blocker_id = u.id AND b.blocked_id = ?)) " +
      "ORDER BY u.username COLLATE NOCASE LIMIT 20"
    ).bind(query + "%", userId, userId, userId).all();
    return json({ players: (result.results || []).map(safeUser) });
  }

  if (path === "/friends" && method === "GET") {
    const result = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT u.id, u.username, u.role, u.created_at FROM social_friendships f " +
      "JOIN users u ON u.id = CASE WHEN f.user_low_id = ? THEN f.user_high_id ELSE f.user_low_id END " +
      "WHERE f.user_low_id = ? OR f.user_high_id = ? ORDER BY u.username COLLATE NOCASE LIMIT 200"
    ).bind(userId, userId, userId).all();
    const friends = [];
    for (const row of result.results || []) {
      if (!(await isBlocked(env, userId, String(row.id)))) friends.push(safeUser(row));
    }
    return json({ friends });
  }

  if (path === "/requests" && method === "GET") {
    const result = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT fr.id, fr.sender_id, fr.recipient_id, fr.created_at, " +
      "s.username AS sender_username, r.username AS recipient_username " +
      "FROM social_friend_requests fr JOIN users s ON s.id = fr.sender_id " +
      "JOIN users r ON r.id = fr.recipient_id " +
      "WHERE (fr.sender_id = ? OR fr.recipient_id = ?) AND fr.status = 'pending' " +
      "ORDER BY fr.created_at DESC LIMIT 100"
    ).bind(userId, userId).all();
    return json({
      requests: (result.results || []).map(row => ({
        id: Number(row.id),
        direction: String(row.sender_id) === userId ? "outgoing" : "incoming",
        username: String(row.sender_id) === userId ? String(row.recipient_username) : String(row.sender_username),
        createdAt: new Date(Number(row.created_at)).toISOString()
      }))
    });
  }

  if (path === "/requests" && method === "POST") {
    await rateLimit(env, "friend-request", userId, 20, 60 * 60 * 1000);
    const body = await readJson(request);
    if (!body) fail(400, "Send a valid JSON request.");
    const username = cleanText(body.username, 20);
    if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) fail(400, "Enter a valid VoidCraft username.");
    const target = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT id, username FROM users WHERE username = ? COLLATE NOCASE LIMIT 1"
    ).bind(username).first();
    if (!target) fail(404, "Player not found.");
    const targetId = String(target.id);
    if (targetId === userId) fail(400, "You cannot add yourself.");
    if (await isBlocked(env, userId, targetId)) fail(403, "A friend request is unavailable for this player.");
    if (await areFriends(env, userId, targetId)) fail(409, "You are already friends.");
    const pair = sortedPair(userId, targetId);
    try {
      await env.VOIDCRAFT_AUTH.prepare(
        "INSERT INTO social_friend_requests (sender_id, recipient_id, pair_key, status, created_at) " +
        "VALUES (?, ?, ?, 'pending', ?)"
      ).bind(userId, targetId, pair[0] + ":" + pair[1], Date.now()).run();
    } catch (error) {
      if (/unique|constraint/i.test(String(error?.message || ""))) {
        fail(409, "A request is already waiting. Check your requests panel.");
      }
      throw error;
    }
    return json({ ok: true }, 201);
  }

  const requestAccept = /^\/requests\/([0-9]+)\/accept$/.exec(path);
  if (requestAccept && method === "POST") {
    const requestId = Number(requestAccept[1]);
    const pending = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT id, sender_id, recipient_id FROM social_friend_requests " +
      "WHERE id = ? AND recipient_id = ? AND status = 'pending' LIMIT 1"
    ).bind(requestId, userId).first();
    if (!pending) fail(404, "Incoming friend request not found.");
    const peerId = String(pending.sender_id);
    if (await isBlocked(env, userId, peerId)) fail(403, "This request cannot be accepted.");
    const pair = sortedPair(userId, peerId);
    await env.VOIDCRAFT_AUTH.batch([
      env.VOIDCRAFT_AUTH.prepare(
        "INSERT INTO social_friendships (user_low_id, user_high_id, created_at) VALUES (?, ?, ?) " +
        "ON CONFLICT(user_low_id, user_high_id) DO NOTHING"
      ).bind(pair[0], pair[1], Date.now()),
      env.VOIDCRAFT_AUTH.prepare(
        "UPDATE social_friend_requests SET status = 'accepted' WHERE id = ? AND recipient_id = ? AND status = 'pending'"
      ).bind(requestId, userId)
    ]);
    return json({ ok: true });
  }

  const requestCancel = /^\/requests\/([0-9]+)$/.exec(path);
  if (requestCancel && method === "DELETE") {
    const result = await env.VOIDCRAFT_AUTH.prepare(
      "UPDATE social_friend_requests SET status = 'rejected' " +
      "WHERE id = ? AND (sender_id = ? OR recipient_id = ?) AND status = 'pending'"
    ).bind(Number(requestCancel[1]), userId, userId).run();
    if (Number(result.meta?.changes || 0) < 1) fail(404, "Pending friend request not found.");
    return json({ ok: true });
  }

  const friendDelete = /^\/friends\/([^/]+)$/.exec(path);
  if (friendDelete && method === "DELETE") {
    const peer = await requireOtherUser(env, decodeURIComponent(friendDelete[1]), userId);
    const pair = sortedPair(userId, String(peer.id));
    await env.VOIDCRAFT_AUTH.prepare(
      "DELETE FROM social_friendships WHERE user_low_id = ? AND user_high_id = ?"
    ).bind(pair[0], pair[1]).run();
    return json({ ok: true });
  }

  if (path === "/conversations" && method === "GET") {
    const result = await env.VOIDCRAFT_AUTH.prepare(
      "WITH latest AS (" +
      " SELECT CASE WHEN sender_id = ? THEN recipient_id ELSE sender_id END AS peer_id, MAX(id) AS last_id " +
      " FROM social_messages WHERE sender_id = ? OR recipient_id = ? " +
      " GROUP BY CASE WHEN sender_id = ? THEN recipient_id ELSE sender_id END" +
      ") " +
      "SELECT u.id AS user_id, u.username, m.body AS last_message, m.created_at AS last_message_at, m.id AS message_id " +
      "FROM latest l JOIN social_messages m ON m.id = l.last_id JOIN users u ON u.id = l.peer_id " +
      "WHERE EXISTS (SELECT 1 FROM social_friendships f WHERE " +
      "(f.user_low_id = ? AND f.user_high_id = u.id) OR (f.user_high_id = ? AND f.user_low_id = u.id)) " +
      "AND NOT EXISTS (SELECT 1 FROM social_blocks b WHERE " +
      "(b.blocker_id = ? AND b.blocked_id = u.id) OR (b.blocker_id = u.id AND b.blocked_id = ?)) " +
      "ORDER BY m.id DESC LIMIT 50"
    ).bind(userId, userId, userId, userId, userId, userId, userId, userId).all();
    return json({
      conversations: (result.results || []).map(row => ({
        id: String(row.user_id),
        username: String(row.username),
        lastMessage: String(row.last_message || ""),
        createdAt: new Date(Number(row.last_message_at)).toISOString(),
        messageId: Number(row.message_id)
      }))
    });
  }

  const messagesMatch = /^\/conversations\/([^/]+)\/messages$/.exec(path);
  if (messagesMatch) {
    const peer = await requireOtherUser(env, decodeURIComponent(messagesMatch[1]), userId);
    const peerId = String(peer.id);
    await requireFriendConversation(env, userId, peerId);
    if (method === "GET") {
      await rateLimit(env, "message-read", userId, 120, 60 * 1000);
      const rawBefore = url.searchParams.get("before");
      const before = rawBefore === null ? null : Number(rawBefore);
      if (before !== null && (!Number.isSafeInteger(before) || before < 1)) fail(400, "Invalid message cursor.");
      const rawLimit = Number(url.searchParams.get("limit") || "40");
      if (!Number.isSafeInteger(rawLimit) || rawLimit < 1 || rawLimit > 50) fail(400, "Message limit must be 1–50.");
      const result = await env.VOIDCRAFT_AUTH.prepare(
        "SELECT m.id, m.sender_id, m.recipient_id, m.body, m.created_at, u.username AS sender_username " +
        "FROM social_messages m JOIN users u ON u.id = m.sender_id " +
        "WHERE ((m.sender_id = ? AND m.recipient_id = ?) OR (m.sender_id = ? AND m.recipient_id = ?)) " +
        "AND (? IS NULL OR m.id < ?) ORDER BY m.id DESC LIMIT ?"
      ).bind(userId, peerId, peerId, userId, before, before, rawLimit).all();
      return json({
        messages: (result.results || []).slice().reverse().map(row => ({
          id: Number(row.id),
          senderId: String(row.sender_id),
          recipientId: String(row.recipient_id),
          senderUsername: String(row.sender_username),
          body: String(row.body),
          createdAt: new Date(Number(row.created_at)).toISOString()
        }))
      });
    }
    if (method === "POST") {
      await rateLimit(env, "message-send", userId, 30, 60 * 1000);
      const body = await readJson(request);
      if (!body) fail(400, "Send a valid JSON request.");
      const content = cleanText(body.content, 2000);
      if (!content || new TextEncoder().encode(content).byteLength > 6000) {
        fail(400, "Message must contain 1–2000 characters.");
      }
      const createdAt = Date.now();
      const inserted = await env.VOIDCRAFT_AUTH.prepare(
        "INSERT INTO social_messages (sender_id, recipient_id, body, created_at) VALUES (?, ?, ?, ?)"
      ).bind(userId, peerId, content, createdAt).run();
      return json({
        ok: true,
        message: {
          id: Number(inserted.meta?.last_row_id || 0),
          senderId: userId,
          recipientId: peerId,
          senderUsername: String(user.username),
          body: content,
          createdAt: new Date(createdAt).toISOString()
        }
      }, 201);
    }
    fail(405, "Use GET to read messages or POST to send one.");
  }

  if (path === "/blocks" && method === "GET") {
    const result = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT u.id, u.username, u.role, b.created_at FROM social_blocks b " +
      "JOIN users u ON u.id = b.blocked_id WHERE b.blocker_id = ? ORDER BY b.created_at DESC LIMIT 200"
    ).bind(userId).all();
    return json({ blocks: (result.results || []).map(row => safeUser(row)) });
  }

  const blockMatch = /^\/blocks\/([^/]+)$/.exec(path);
  if (blockMatch && method === "POST") {
    await rateLimit(env, "block-player", userId, 30, 60 * 60 * 1000);
    const peer = await requireOtherUser(env, decodeURIComponent(blockMatch[1]), userId);
    const peerId = String(peer.id);
    const pair = sortedPair(userId, peerId);
    await env.VOIDCRAFT_AUTH.batch([
      env.VOIDCRAFT_AUTH.prepare(
        "INSERT INTO social_blocks (blocker_id, blocked_id, created_at) VALUES (?, ?, ?) " +
        "ON CONFLICT(blocker_id, blocked_id) DO NOTHING"
      ).bind(userId, peerId, Date.now()),
      env.VOIDCRAFT_AUTH.prepare(
        "DELETE FROM social_friendships WHERE user_low_id = ? AND user_high_id = ?"
      ).bind(pair[0], pair[1]),
      env.VOIDCRAFT_AUTH.prepare(
        "UPDATE social_friend_requests SET status = 'rejected' WHERE status = 'pending' " +
        "AND ((sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?))"
      ).bind(userId, peerId, peerId, userId)
    ]);
    return json({ ok: true });
  }

  if (blockMatch && method === "DELETE") {
    await env.VOIDCRAFT_AUTH.prepare(
      "DELETE FROM social_blocks WHERE blocker_id = ? AND blocked_id = ?"
    ).bind(userId, decodeURIComponent(blockMatch[1])).run();
    return json({ ok: true });
  }

  if (path === "/reports" && method === "POST") {
    await rateLimit(env, "player-report", userId, 10, 60 * 60 * 1000);
    const body = await readJson(request);
    if (!body) fail(400, "Send a valid JSON request.");
    const targetId = String(body.targetUserId || "");
    const target = await requireOtherUser(env, targetId, userId);
    const category = cleanText(body.category, 40);
    const allowed = new Set(["harassment", "spam", "impersonation", "inappropriate-content", "other"]);
    if (!allowed.has(category)) fail(400, "Choose a valid report category.");
    const details = cleanText(body.details, 1000);
    const result = await env.VOIDCRAFT_AUTH.prepare(
      "INSERT INTO social_reports (reporter_id, target_id, category, details, status, created_at) " +
      "VALUES (?, ?, ?, ?, 'open', ?)"
    ).bind(userId, String(target.id), category, details, Date.now()).run();
    return json({ ok: true, reportId: Number(result.meta?.last_row_id || 0) }, 201);
  }

  if (path === "/moderation/reports" && method === "GET") {
    if (user.role !== "owner") fail(403, "Owner permissions are required for the report queue.");
    const requestedStatus = url.searchParams.get("status") || "open";
    if (!new Set(["open", "reviewing", "resolved", "dismissed"]).has(requestedStatus)) {
      fail(400, "Choose a valid report status.");
    }
    const result = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT r.id, r.reporter_id, reporter.username AS reporter_username, r.target_id, " +
      "target.username AS target_username, r.category, r.details, r.status, r.created_at " +
      "FROM social_reports r JOIN users reporter ON reporter.id = r.reporter_id " +
      "JOIN users target ON target.id = r.target_id WHERE r.status = ? " +
      "ORDER BY r.created_at DESC LIMIT 100"
    ).bind(requestedStatus).all();
    return json({
      reports: (result.results || []).map(row => ({
        id: Number(row.id),
        reporterUsername: String(row.reporter_username),
        targetUsername: String(row.target_username),
        category: String(row.category),
        details: String(row.details),
        status: String(row.status),
        createdAt: new Date(Number(row.created_at)).toISOString()
      }))
    });
  }

  const moderationMatch = /^\/moderation\/reports\/([0-9]+)$/.exec(path);
  if (moderationMatch && method === "PATCH") {
    if (user.role !== "owner") fail(403, "Owner permissions are required to update reports.");
    await rateLimit(env, "moderation-update", userId, 60, 60 * 1000);
    const body = await readJson(request);
    if (!body) fail(400, "Send a valid JSON request.");
    const status = cleanText(body.status, 20);
    if (!new Set(["open", "reviewing", "resolved", "dismissed"]).has(status)) {
      fail(400, "Choose a valid report status.");
    }
    const result = await env.VOIDCRAFT_AUTH.prepare(
      "UPDATE social_reports SET status = ? WHERE id = ?"
    ).bind(status, Number(moderationMatch[1])).run();
    if (Number(result.meta?.changes || 0) < 1) fail(404, "Report not found.");
    return json({ ok: true });
  }

  fail(404, "Chat endpoint not found.");
}

export async function onRequest({ request, env }) {
  if (!env?.VOIDCRAFT_AUTH) {
    return json({ error: "The website account database is not connected." }, 503);
  }
  try {
    if (!new URL(request.url).pathname.startsWith("/api/chat")) fail(404, "Chat endpoint not found.");
    const method = request.method.toUpperCase();
    if (["POST", "PATCH", "PUT", "DELETE"].includes(method) && !sameOrigin(request)) {
      fail(403, "Request origin could not be verified. Reload the page and try again.");
    }
    if (!["GET", "POST", "PATCH", "DELETE"].includes(method)) fail(405, "HTTP method not allowed.");
    const user = await currentUser(request, env);
    if (!user) fail(401, "Sign in with your VoidCraft website account to use Chat.");
    return await route(request, env, user);
  } catch (error) {
    if (error instanceof ChatError) return json({ error: error.message }, error.status);
    const message = String(error?.message || "");
    if (/no such table|no such column/i.test(message)) {
      return json({
        error: "Website Chat needs database migration 0005_social_chat.sql applied to the existing account database."
      }, 503);
    }
    console.error("VoidCraft Chat request failed", { name: error?.name || "Error" });
    return json({ error: "The Chat service could not complete that request. Please try again." }, 500);
  }
}
