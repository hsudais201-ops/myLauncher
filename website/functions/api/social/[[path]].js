import { currentUser, json, readJson, sameOrigin } from "../auth/_utils.js";

const now = () => Date.now();
const id = () => crypto.randomUUID();
const clean = (v, n = 2000) => String(v ?? "").replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "").trim().slice(0, n);
const fail = (message, status = 400) => json({ error: message }, status);

async function signedIn(request, env) {
  if (!env?.VOIDCRAFT_AUTH) return { response: json({ error: "Account service is not connected." }, 503) };
  const user = await currentUser(request, env);
  if (!user) return { response: json({ error: "Sign in to your VoidCraft website account first." }, 401) };
  return { user };
}
async function blockedEither(db, a, b) {
  return !!(await db.prepare("SELECT 1 AS found FROM social_blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?) LIMIT 1").bind(a,b,b,a).first());
}
function publicUser(u) {
  return { id: String(u.id), username: String(u.username), displayName: String(u.display_name || u.username), role: u.role === "owner" ? "owner" : "member" };
}
function orderPair(a,b) { return String(a) < String(b) ? [String(a),String(b)] : [String(b),String(a)]; }

export async function onRequest({ request, env }) {
  const { user, response } = await signedIn(request, env);
  if (response) return response;
  const db = env.VOIDCRAFT_AUTH;
  const url = new URL(request.url);
  const path = url.pathname.replace(/^\/api\/social\/?/, "").replace(/\/$/, "");
  const method = request.method.toUpperCase();

  try {
    if (method !== "GET" && !sameOrigin(request)) return fail("Request origin could not be verified.", 403);

    if (path === "me" && method === "GET") {
      return json({ user: publicUser(user) });
    }

    if (path === "players" && method === "GET") {
      const q = clean(url.searchParams.get("q"), 32);
      if (q.length < 2) return json({ players: [] });
      const rows = await db.prepare("SELECT id,username,role FROM users WHERE username COLLATE NOCASE LIKE ? ESCAPE '\' AND id<>? ORDER BY username LIMIT 20")
        .bind("%" + q.replace(/[\\%_]/g, ch => "\\" + ch) + "%", user.id).all();
      return json({ players: (rows.results || []).map(publicUser) });
    }

    if (path === "friends" && method === "GET") {
      const rows = await db.prepare(
        "SELECT u.id,u.username,u.role FROM social_friendships f JOIN users u ON u.id=CASE WHEN f.user_low=? THEN f.user_high ELSE f.user_low END WHERE f.user_low=? OR f.user_high=? ORDER BY u.username"
      ).bind(user.id,user.id,user.id).all();
      return json({ friends: (rows.results || []).map(publicUser) });
    }

    if (path === "friends/requests" && method === "GET") {
      const incoming = await db.prepare("SELECT r.id,r.created_at,u.id AS user_id,u.username,u.role FROM social_friend_requests r JOIN users u ON u.id=r.sender_id WHERE r.recipient_id=? AND r.status='pending' ORDER BY r.created_at DESC LIMIT 50").bind(user.id).all();
      const outgoing = await db.prepare("SELECT r.id,r.created_at,u.id AS user_id,u.username,u.role FROM social_friend_requests r JOIN users u ON u.id=r.recipient_id WHERE r.sender_id=? AND r.status='pending' ORDER BY r.created_at DESC LIMIT 50").bind(user.id).all();
      return json({ incoming: (incoming.results || []).map(r=>({id:r.id,createdAt:r.created_at,user:publicUser({id:r.user_id,username:r.username,role:r.role})})), outgoing:(outgoing.results||[]).map(r=>({id:r.id,createdAt:r.created_at,user:publicUser({id:r.user_id,username:r.username,role:r.role})})) });
    }

    if (path === "friends/requests" && method === "POST") {
      const body = await readJson(request);
      const username = clean(body?.username, 20);
      if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) return fail("Enter a valid username.");
      const requestWindow = now() - 10 * 60 * 1000;
      const recentRequests = await db.prepare("SELECT COUNT(*) AS total FROM social_friend_requests WHERE sender_id=? AND created_at>=?").bind(user.id,requestWindow).first();
      if (Number(recentRequests?.total || 0) >= 15) return fail("You have sent too many friend requests. Try again in a few minutes.",429);
      const target = await db.prepare("SELECT id,username,role FROM users WHERE username=? COLLATE NOCASE").bind(username).first();
      if (!target) return fail("No VoidCraft account was found with that username.", 404);
      if (String(target.id) === String(user.id)) return fail("You cannot add yourself.");
      if (await blockedEither(db,user.id,target.id)) return fail("This friend request is unavailable.", 403);
      const [low,high] = orderPair(user.id,target.id);
      const friendship = await db.prepare("SELECT 1 FROM social_friendships WHERE user_low=? AND user_high=?").bind(low,high).first();
      if (friendship) return fail("You are already friends.");
      const pending = await db.prepare("SELECT id FROM social_friend_requests WHERE status='pending' AND ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?)) LIMIT 1").bind(user.id,target.id,target.id,user.id).first();
      if (pending) return fail("A pending friend request already exists.");
      const requestId=id();
      try {
        await db.prepare("INSERT INTO social_friend_requests(id,sender_id,recipient_id,status,created_at) VALUES(?,?,?,'pending',?)").bind(requestId,user.id,target.id,now()).run();
      } catch (error) {
        // The database's unordered-pair unique index closes the simultaneous-request race.
        const raced = await db.prepare("SELECT id FROM social_friend_requests WHERE status='pending' AND ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?)) LIMIT 1").bind(user.id,target.id,target.id,user.id).first();
        if (raced) return fail("A pending friend request already exists.",409);
        throw error;
      }
      return json({ request:{id:requestId,user:publicUser(target)} },201);
    }

    let match = path.match(/^friends\/requests\/([0-9a-f-]+)\/accept$/i);
    if (match && method === "POST") {
      const req = await db.prepare("SELECT * FROM social_friend_requests WHERE id=? AND recipient_id=? AND status='pending'").bind(match[1],user.id).first();
      if (!req) return fail("Friend request not found.",404);
      if (await blockedEither(db,req.sender_id,req.recipient_id)) return fail("Unblock this player before accepting the request.",403);
      const [low,high]=orderPair(req.sender_id,req.recipient_id);
      await db.batch([
        db.prepare("INSERT OR IGNORE INTO social_friendships(user_low,user_high,created_at) VALUES(?,?,?)").bind(low,high,now()),
        db.prepare("UPDATE social_friend_requests SET status='accepted' WHERE id=?").bind(req.id)
      ]);
      return json({ ok:true });
    }
    match = path.match(/^friends\/requests\/([0-9a-f-]+)$/i);
    if (match && method === "DELETE") {
      const result = await db.prepare("UPDATE social_friend_requests SET status=CASE WHEN sender_id=? THEN 'cancelled' ELSE 'rejected' END WHERE id=? AND status='pending' AND (sender_id=? OR recipient_id=?)").bind(user.id,match[1],user.id,user.id).run();
      if (Number(result?.meta?.changes || 0) < 1) return fail("Pending friend request not found.",404);
      return json({ ok:true });
    }
    match = path.match(/^friends\/([^/]+)$/);
    if (match && method === "DELETE") {
      const [low,high]=orderPair(user.id,match[1]);
      await db.prepare("DELETE FROM social_friendships WHERE user_low=? AND user_high=?").bind(low,high).run();
      return json({ ok:true });
    }

    if (path === "conversations" && method === "GET") {
      const rows = await db.prepare(
        "SELECT u.id,u.username,u.role,MAX(m.created_at) AS last_at,(SELECT body FROM social_messages m2 WHERE ((m2.sender_id=? AND m2.recipient_id=u.id) OR (m2.sender_id=u.id AND m2.recipient_id=?)) ORDER BY m2.created_at DESC LIMIT 1) AS last_message FROM users u JOIN social_friendships f ON (f.user_low=? AND f.user_high=u.id) OR (f.user_high=? AND f.user_low=u.id) JOIN social_messages m ON (m.sender_id=? AND m.recipient_id=u.id) OR (m.sender_id=u.id AND m.recipient_id=?) WHERE NOT EXISTS (SELECT 1 FROM social_blocks b WHERE (b.blocker_id=? AND b.blocked_id=u.id) OR (b.blocker_id=u.id AND b.blocked_id=?)) GROUP BY u.id ORDER BY last_at DESC LIMIT 50"
      ).bind(user.id,user.id,user.id,user.id,user.id,user.id,user.id,user.id).all();
      return json({ conversations:(rows.results||[]).map(r=>({user:publicUser(r),lastMessage:r.last_message,lastAt:r.last_at})) });
    }

    match = path.match(/^conversations\/([^/]+)\/messages$/);
    if (match && method === "GET") {
      const other=match[1];
      const [low,high]=orderPair(user.id,other);
      const friendship=await db.prepare("SELECT 1 FROM social_friendships WHERE user_low=? AND user_high=?").bind(low,high).first();
      if (!friendship) return fail("You must be friends to view private messages.",403);
      if (await blockedEither(db,user.id,other)) return fail("Messaging is unavailable for this player.",403);
      const beforeParam=url.searchParams.get("before");
      const limitParam=url.searchParams.get("limit");
      const before=beforeParam===null ? Number.MAX_SAFE_INTEGER : Number(beforeParam);
      const limit=limitParam===null ? 40 : Number(limitParam);
      if (!Number.isSafeInteger(before) || before < 0) return fail("Invalid message pagination cursor.");
      if (!Number.isSafeInteger(limit) || limit < 1 || limit > 100) return fail("Message limit must be between 1 and 100.");
      const rows=await db.prepare("SELECT id,sender_id,recipient_id,body,created_at FROM social_messages WHERE ((sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?)) AND created_at<? ORDER BY created_at DESC LIMIT ?").bind(user.id,other,other,user.id,before,limit).all();
      return json({messages:(rows.results||[]).reverse().map(m=>({id:m.id,senderId:m.sender_id,recipientId:m.recipient_id,content:m.body,createdAt:m.created_at}))});
    }
    if (match && method === "POST") {
      const other=match[1], body=await readJson(request);
      const rawContent = typeof body?.content === "string" ? body.content : "";
      const content = rawContent.replace(/[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]/g, "").trim();
      if (!content) return fail("Message cannot be empty.");
      if (content.length > 2000) return fail("Messages must be 2000 characters or fewer.");
      const [low,high]=orderPair(user.id,other);
      const friendship=await db.prepare("SELECT 1 FROM social_friendships WHERE user_low=? AND user_high=?").bind(low,high).first();
      if (!friendship) return fail("You must be friends to send private messages.",403);
      if (await blockedEither(db,user.id,other)) return fail("Messaging is unavailable for this player.",403);
      const createdAt = now();
      const recentMessages = await db.prepare("SELECT COUNT(*) AS total FROM social_messages WHERE sender_id=? AND created_at>=?").bind(user.id,createdAt-60_000).first();
      if (Number(recentMessages?.total || 0) >= 30) return fail("You are sending messages too quickly. Please wait a minute.",429);
      const msgId=id();
      await db.prepare("INSERT INTO social_messages(id,sender_id,recipient_id,body,created_at) VALUES(?,?,?,?,?)").bind(msgId,user.id,other,content,createdAt).run();
      return json({message:{id:msgId,senderId:user.id,recipientId:other,content,createdAt}},201);
    }

    if (path === "blocks" && method === "GET") {
      const rows=await db.prepare("SELECT u.id,u.username,u.role FROM social_blocks b JOIN users u ON u.id=b.blocked_id WHERE b.blocker_id=? ORDER BY u.username").bind(user.id).all();
      return json({blocks:(rows.results||[]).map(publicUser)});
    }
    match=path.match(/^blocks\/([^/]+)$/);
    if (match && method === "POST") {
      if (String(match[1])===String(user.id)) return fail("You cannot block yourself.");
      const target=await db.prepare("SELECT id FROM users WHERE id=?").bind(match[1]).first();
      if (!target) return fail("Player not found.",404);
      const [low,high]=orderPair(user.id,match[1]);
      await db.batch([
        db.prepare("INSERT OR IGNORE INTO social_blocks(blocker_id,blocked_id,created_at) VALUES(?,?,?)").bind(user.id,match[1],now()),
        db.prepare("DELETE FROM social_friendships WHERE user_low=? AND user_high=?").bind(low,high)
      ]);
      return json({ok:true},201);
    }
    if (match && method === "DELETE") {
      await db.prepare("DELETE FROM social_blocks WHERE blocker_id=? AND blocked_id=?").bind(user.id,match[1]).run();
      return json({ok:true});
    }

    if (path === "reports" && method === "POST") {
      const body=await readJson(request), targetId=clean(body?.targetUserId,64), category=clean(body?.category,40), details=clean(body?.details,1000);
      if (!["harassment","spam","inappropriate","impersonation","other"].includes(category)) return fail("Choose a valid report reason.");
      if (!targetId || targetId===String(user.id)) return fail("Choose another player to report.");
      const target=await db.prepare("SELECT id FROM users WHERE id=?").bind(targetId).first();
      if (!target) return fail("Player not found.",404);
      const createdAt=now();
      const recentReports=await db.prepare("SELECT COUNT(*) AS total FROM social_reports WHERE reporter_id=? AND created_at>=?").bind(user.id,createdAt-60*60*1000).first();
      if (Number(recentReports?.total || 0) >= 10) return fail("You have submitted too many reports. Please try again later.",429);
      const reportId=id();
      await db.prepare("INSERT INTO social_reports(id,reporter_id,target_user_id,category,details,status,created_at) VALUES(?,?,?,?,?,'open',?)").bind(reportId,user.id,targetId,category,details,createdAt).run();
      return json({report:{id:reportId,status:"open"}},201);
    }
    if (path === "moderation/reports" && method === "GET") {
      if (user.role!=="owner") return fail("Owner permission required.",403);
      const status=clean(url.searchParams.get("status")||"open",16);
      if (!["open","reviewed","dismissed"].includes(status)) return fail("Invalid moderation status.");
      const rows=await db.prepare("SELECT r.id,r.category,r.details,r.status,r.created_at,reporter.username AS reporter,target.username AS target FROM social_reports r JOIN users reporter ON reporter.id=r.reporter_id JOIN users target ON target.id=r.target_user_id WHERE r.status=? ORDER BY r.created_at DESC LIMIT 50").bind(status).all();
      return json({reports:rows.results||[]});
    }
    match=path.match(/^moderation\/reports\/([^/]+)$/);
    if (match && method === "PATCH") {
      if (user.role!=="owner") return fail("Owner permission required.",403);
      const body=await readJson(request), status=clean(body?.status,16);
      if (!["reviewed","dismissed"].includes(status)) return fail("Invalid moderation status.");
      const result=await db.prepare("UPDATE social_reports SET status=?,reviewed_by=?,reviewed_at=? WHERE id=?").bind(status,user.id,now(),match[1]).run();
      if (Number(result?.meta?.changes || 0) < 1) return fail("Report not found.",404);
      return json({ok:true});
    }

    return fail("Social Hub endpoint not found.",404);
  } catch (error) {
    console.error("VoidCraft Social Hub request failed", { name:error?.name||"Error", path, method });
    return json({ error:"The Social Hub request could not be completed. Please try again." },500);
  }
}
