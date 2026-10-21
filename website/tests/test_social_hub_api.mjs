import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { DatabaseSync } from "node:sqlite";
import { pathToFileURL } from "node:url";
import { afterEach, beforeEach, test } from "node:test";
import { webcrypto } from "node:crypto";

if (!globalThis.crypto) globalThis.crypto = webcrypto;
const handlerPath = process.env.SOCIAL_HUB_HANDLER;
if (!handlerPath) throw new Error("SOCIAL_HUB_HANDLER must point to the prepared Pages Function module");
const { onRequest } = await import(pathToFileURL(handlerPath).href);
const migration = await readFile(new URL("../migrations/0005_social_hub.sql", import.meta.url), "utf8");

class D1Statement {
  constructor(database, sql, params = []) { this.database = database; this.sql = sql; this.params = params; }
  bind(...params) { return new D1Statement(this.database, this.sql, params); }
  async first() { return this.database.prepare(this.sql).get(...this.params) ?? null; }
  async all() { return { results: this.database.prepare(this.sql).all(...this.params) }; }
  async run() {
    const result = this.database.prepare(this.sql).run(...this.params);
    return { meta: { changes: Number(result.changes || 0), last_row_id: Number(result.lastInsertRowid || 0) } };
  }
}
class D1Adapter {
  constructor(database) { this.database = database; }
  prepare(sql) { return new D1Statement(this.database, sql); }
  async batch(statements) {
    this.database.exec("BEGIN");
    try {
      const results = [];
      for (const statement of statements) results.push(await statement.run());
      this.database.exec("COMMIT");
      return results;
    } catch (error) {
      this.database.exec("ROLLBACK");
      throw error;
    }
  }
}

let database;
let env;
let tokens;
const baseUrl = "https://voidcraft.test";
const tokenFor = (letter) => letter.repeat(64);

async function sha256Hex(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}
async function createUser(id, username, role = "member") {
  database.prepare("INSERT INTO users(id,username,email,password_salt,password_hash,created_at,role) VALUES(?,?,?,?,?,?,?)")
    .run(id, username, username + "@example.test", "salt", "hash", "2026-01-01", role);
}
async function createSession(id, userId, token) {
  const now = Math.floor(Date.now() / 1000);
  database.prepare("INSERT INTO sessions(id,user_id,token_hash,created_at,expires_at) VALUES(?,?,?,?,?)")
    .run(id, userId, await sha256Hex(token), now, now + 3600);
}
async function call(path, { as, method = "GET", body, origin = baseUrl } = {}) {
  const headers = new Headers();
  if (as && tokens[as]) headers.set("Cookie", "__Host-vc_session=" + tokens[as]);
  if (method !== "GET" && method !== "HEAD") headers.set("Origin", origin);
  const init = { method, headers };
  if (body !== undefined) { headers.set("Content-Type", "application/json"); init.body = JSON.stringify(body); }
  const request = new Request(baseUrl + "/api/social/" + path, init);
  const response = await onRequest({ request, env });
  let data = {};
  try { data = await response.json(); } catch {}
  return { status: response.status, data };
}
function orderPair(a, b) { return String(a) < String(b) ? [String(a), String(b)] : [String(b), String(a)]; }

beforeEach(async () => {
  database = new DatabaseSync(":memory:");
  database.exec("PRAGMA foreign_keys = ON");
  database.exec("CREATE TABLE users (id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE COLLATE NOCASE, email TEXT NOT NULL UNIQUE COLLATE NOCASE, password_salt TEXT NOT NULL, password_hash TEXT NOT NULL, created_at TEXT NOT NULL, last_login_at TEXT, role TEXT NOT NULL DEFAULT 'member' CHECK(role IN ('member','owner')))");
  database.exec("CREATE TABLE sessions (id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE, token_hash TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL)");
  await createUser("u-alice", "alice");
  await createUser("u-bob", "bob");
  await createUser("u-owner", "owner", "owner");
  await createUser("u-literal", "playerXone");
  for (let i = 0; i < 55; i++) {
    const suffix = String(i).padStart(2, "0");
    await createUser("p" + suffix, "player_" + suffix);
  }
  database.exec(migration);
  tokens = { alice: tokenFor("a"), bob: tokenFor("b"), owner: tokenFor("c") };
  await createSession("s-alice", "u-alice", tokens.alice);
  await createSession("s-bob", "u-bob", tokens.bob);
  await createSession("s-owner", "u-owner", tokens.owner);
  env = { VOIDCRAFT_AUTH: new D1Adapter(database) };
});
afterEach(() => {
  if (database) database.close();
  database = undefined;
});

test("requires an existing website session and rejects cross-origin mutations", async () => {
  const anonymous = await call("me");
  assert.equal(anonymous.status, 401);
  assert.match(anonymous.data.error, /Sign in/);
  const signedIn = await call("me", { as: "alice" });
  assert.equal(signedIn.status, 200);
  assert.equal(signedIn.data.user.username, "alice");
  assert.equal(signedIn.data.user.role, "member");
  const csrf = await call("friends/requests", { as: "alice", method: "POST", origin: "https://attacker.example", body: { username: "bob" } });
  assert.equal(csrf.status, 403);
});

test("two website accounts can request, accept, and exchange private messages", async () => {
  const sent = await call("friends/requests", { as: "alice", method: "POST", body: { username: "bob" } });
  assert.equal(sent.status, 201);
  assert.ok(sent.data.request.id);
  const bobRequests = await call("friends/requests", { as: "bob" });
  assert.equal(bobRequests.status, 200);
  assert.equal(bobRequests.data.incoming.length, 1);
  assert.equal(bobRequests.data.incoming[0].user.username, "alice");
  const accepted = await call("friends/requests/" + sent.data.request.id + "/accept", { as: "bob", method: "POST", body: {} });
  assert.equal(accepted.status, 200);
  const friends = await call("friends", { as: "alice" });
  assert.equal(friends.status, 200);
  assert.equal(friends.data.friends[0].username, "bob");
  const message = await call("conversations/u-bob/messages", { as: "alice", method: "POST", body: { content: "Hello from VoidCraft 🌌" } });
  assert.equal(message.status, 201);
  assert.equal(message.data.message.content, "Hello from VoidCraft 🌌");
  const cleaned = await call("conversations/u-bob/messages", { as: "alice", method: "POST", body: { content: "\u0001clean\u007f" } });
  assert.equal(cleaned.status, 201);
  assert.equal(cleaned.data.message.content, "clean");

  const emptyMessage = await call("conversations/u-bob/messages", { as: "alice", method: "POST", body: { content: "   " } });
  assert.equal(emptyMessage.status, 400);
  const oversizedMessage = await call("conversations/u-bob/messages", { as: "alice", method: "POST", body: { content: "x".repeat(2001) } });
  assert.equal(oversizedMessage.status, 400);

  const invalidLimit = await call("conversations/u-alice/messages?limit=101", { as: "bob" });
  assert.equal(invalidLimit.status, 400);
  const invalidCursor = await call("conversations/u-alice/messages?before=-1", { as: "bob" });
  assert.equal(invalidCursor.status, 400);

  const received = await call("conversations/u-alice/messages?limit=40", { as: "bob" });
  assert.equal(received.status, 200);
  assert.equal(received.data.messages.length, 2);
  assert.ok(received.data.messages.some((item) => item.content === "Hello from VoidCraft 🌌"));
  assert.ok(received.data.messages.some((item) => item.content === "clean"));
});

test("removing a friend immediately revokes private-message access", async () => {
  const sent = await call("friends/requests", { as: "alice", method: "POST", body: { username: "bob" } });
  assert.equal(sent.status, 201);
  const accepted = await call("friends/requests/" + sent.data.request.id + "/accept", { as: "bob", method: "POST", body: {} });
  assert.equal(accepted.status, 200);
  const message = await call("conversations/u-bob/messages", { as: "alice", method: "POST", body: { content: "Before removal" } });
  assert.equal(message.status, 201);
  const removed = await call("friends/u-bob", { as: "alice", method: "DELETE" });
  assert.equal(removed.status, 200);
  const history = await call("conversations/u-bob/messages", { as: "alice" });
  assert.equal(history.status, 403);
  const afterRemoval = await call("conversations/u-bob/messages", { as: "alice", method: "POST", body: { content: "After removal" } });
  assert.equal(afterRemoval.status, 403);
});

test("blocking a friend revokes chat and unblocking does not restore friendship", async () => {
  const sent = await call("friends/requests", { as: "alice", method: "POST", body: { username: "bob" } });
  assert.equal(sent.status, 201);
  const accepted = await call("friends/requests/" + sent.data.request.id + "/accept", { as: "bob", method: "POST", body: {} });
  assert.equal(accepted.status, 200);
  const beforeBlock = await call("conversations/u-bob/messages", { as: "alice", method: "POST", body: { content: "Before block" } });
  assert.equal(beforeBlock.status, 201);
  const blocked = await call("blocks/u-bob", { as: "alice", method: "POST", body: {} });
  assert.equal(blocked.status, 201);
  const whileBlocked = await call("conversations/u-bob/messages", { as: "alice", method: "POST", body: { content: "While blocked" } });
  assert.equal(whileBlocked.status, 403);
  const unblocked = await call("blocks/u-bob", { as: "alice", method: "DELETE" });
  assert.equal(unblocked.status, 200);
  const afterUnblock = await call("conversations/u-bob/messages", { as: "alice", method: "POST", body: { content: "After unblock" } });
  assert.equal(afterUnblock.status, 403);
  const friends = await call("friends", { as: "alice" });
  assert.equal(friends.status, 200);
  assert.equal(friends.data.friends.some((friend) => friend.id === "u-bob"), false);
});

test("player search treats underscores as literal characters", async () => {
  const search = await call("players?q=player_", { as: "alice" });
  assert.equal(search.status, 200);
  assert.ok(search.data.players.length > 0);
  assert.ok(search.data.players.every((player) => player.username.includes("_")));
  assert.ok(!search.data.players.some((player) => player.username === "playerXone"));
});

test("rejects duplicate reverse friend requests and requires friendship for messages", async () => {
  const first = await call("friends/requests", { as: "alice", method: "POST", body: { username: "bob" } });
  assert.equal(first.status, 201);
  const reverse = await call("friends/requests", { as: "bob", method: "POST", body: { username: "alice" } });
  assert.equal(reverse.status, 409);
  const message = await call("conversations/u-bob/messages", { as: "alice", method: "POST", body: { content: "Not yet friends" } });
  assert.equal(message.status, 403);
});

test("blocking invalidates incoming and outgoing requests in the correct direction", async () => {
  const incoming = await call("friends/requests", { as: "alice", method: "POST", body: { username: "bob" } });
  assert.equal(incoming.status, 201);
  const bobBlocksAlice = await call("blocks/u-alice", { as: "bob", method: "POST", body: {} });
  assert.equal(bobBlocksAlice.status, 201);
  let row = database.prepare("SELECT status FROM social_friend_requests WHERE id=?").get(incoming.data.request.id);
  assert.equal(row.status, "rejected");

  await call("blocks/u-alice", { as: "bob", method: "DELETE" });
  const outgoing = await call("friends/requests", { as: "alice", method: "POST", body: { username: "bob" } });
  assert.equal(outgoing.status, 201);
  const aliceBlocksBob = await call("blocks/u-bob", { as: "alice", method: "POST", body: {} });
  assert.equal(aliceBlocksBob.status, 201);
  row = database.prepare("SELECT status FROM social_friend_requests WHERE id=?").get(outgoing.data.request.id);
  assert.equal(row.status, "cancelled");
  const blockedRequest = await call("friends/requests", { as: "bob", method: "POST", body: { username: "alice" } });
  assert.equal(blockedRequest.status, 403);
});

test("report submission validates category and target and enters the owner review queue", async () => {
  const selfReport = await call("reports", { as: "alice", method: "POST", body: { targetUserId: "u-alice", category: "spam", details: "self" } });
  assert.equal(selfReport.status, 400);
  const invalidCategory = await call("reports", { as: "alice", method: "POST", body: { targetUserId: "u-bob", category: "ban-everyone", details: "invalid category" } });
  assert.equal(invalidCategory.status, 400);
  const submitted = await call("reports", { as: "alice", method: "POST", body: { targetUserId: "u-bob", category: "harassment", details: "Please review this report." } });
  assert.equal(submitted.status, 201);
  const memberQueue = await call("moderation/reports?status=open", { as: "alice" });
  assert.equal(memberQueue.status, 403);
  const ownerQueue = await call("moderation/reports?status=open", { as: "owner" });
  assert.equal(ownerQueue.status, 200);
  assert.ok(ownerQueue.data.reports.some((report) => report.id === submitted.data.report.id && report.reporter === "alice" && report.target === "bob"));
});

test("owner moderation works while ordinary users are denied", async () => {
  const report = await call("reports", { as: "alice", method: "POST", body: { targetUserId: "u-bob", category: "spam", details: "Test report details" } });
  assert.equal(report.status, 201);
  const denied = await call("moderation/reports", { as: "alice" });
  assert.equal(denied.status, 403);
  const queue = await call("moderation/reports?status=open", { as: "owner" });
  assert.equal(queue.status, 200);
  assert.equal(queue.data.reports.length, 1);
  assert.equal(queue.data.reports[0].details, "Test report details");
  const review = await call("moderation/reports/" + report.data.report.id, { as: "owner", method: "PATCH", body: { status: "reviewed" } });
  assert.equal(review.status, 200);
  const reviewed = await call("moderation/reports?status=reviewed", { as: "owner" });
  assert.equal(reviewed.status, 200);
  assert.equal(reviewed.data.reports.length, 1);
  assert.equal(reviewed.data.reports[0].status, "reviewed");
});

test("conditional inserts enforce all three quotas under concurrent requests", async () => {
  const friendResults = await Promise.all(Array.from({ length: 20 }, (_, i) =>
    call("friends/requests", { as: "alice", method: "POST", body: { username: "player_" + String(i).padStart(2, "0") } })
  ));
  assert.equal(friendResults.filter((result) => result.status === 201).length, 15);
  assert.equal(friendResults.filter((result) => result.status === 429).length, 5);
  assert.equal(database.prepare("SELECT COUNT(*) AS total FROM social_friend_requests WHERE sender_id='u-alice'").get().total, 15);

  for (let i = 20; i < 55; i++) {
    const otherId = "p" + String(i).padStart(2, "0");
    const [low, high] = orderPair("u-alice", otherId);
    database.prepare("INSERT INTO social_friendships(user_low,user_high,created_at) VALUES(?,?,?)").run(low, high, Date.now());
  }
  const messageResults = await Promise.all(Array.from({ length: 35 }, (_, i) => {
    const otherId = "p" + String(i + 20).padStart(2, "0");
    return call("conversations/" + otherId + "/messages", { as: "alice", method: "POST", body: { content: "Concurrent quota test" } });
  }));
  assert.equal(messageResults.filter((result) => result.status === 201).length, 30);
  assert.equal(messageResults.filter((result) => result.status === 429).length, 5);
  assert.equal(database.prepare("SELECT COUNT(*) AS total FROM social_messages WHERE sender_id='u-alice'").get().total, 30);

  const reportResults = await Promise.all(Array.from({ length: 15 }, () =>
    call("reports", { as: "alice", method: "POST", body: { targetUserId: "u-bob", category: "spam", details: "Concurrent report test" } })
  ));
  assert.equal(reportResults.filter((result) => result.status === 201).length, 10);
  assert.equal(reportResults.filter((result) => result.status === 429).length, 5);
  assert.equal(database.prepare("SELECT COUNT(*) AS total FROM social_reports WHERE reporter_id='u-alice'").get().total, 10);
});
