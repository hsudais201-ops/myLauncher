import test from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { DatabaseSync } from "node:sqlite";
import { onRequest } from "../functions/api/chat/[[path]].js";

const migration = await readFile(new URL("../migrations/0005_social_chat.sql", import.meta.url), "utf8");

function makeDatabase() {
  const db = new DatabaseSync(":memory:");
  db.exec("PRAGMA foreign_keys = ON");
  db.exec(
    "CREATE TABLE users (" +
    "id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE, email TEXT NOT NULL UNIQUE, " +
    "password_salt TEXT NOT NULL, password_hash TEXT NOT NULL, created_at TEXT NOT NULL, " +
    "last_login_at TEXT, role TEXT NOT NULL DEFAULT 'member' CHECK (role IN ('member', 'owner')));" +
    "CREATE TABLE sessions (" +
    "id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE, " +
    "token_hash TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL);"
  );
  db.exec(migration);

  function prepare(sql) {
    let values = [];
    return {
      bind(...args) { values = args; return this; },
      first() { return db.prepare(sql).get(...values) || null; },
      all() { return { results: db.prepare(sql).all(...values) }; },
      run() {
        const result = db.prepare(sql).run(...values);
        return {
          meta: {
            changes: Number(result.changes || 0),
            last_row_id: Number(result.lastInsertRowid || 0)
          }
        };
      }
    };
  }

  const d1 = {
    prepare,
    async batch(statements) {
      db.exec("BEGIN");
      try {
        const results = statements.map((statement) => statement.run());
        db.exec("COMMIT");
        return results;
      } catch (error) {
        db.exec("ROLLBACK");
        throw error;
      }
    }
  };
  return { db, d1 };
}

function seedUser(db, { id, username, role = "member", token }) {
  const now = new Date().toISOString();
  db.prepare(
    "INSERT INTO users (id, username, email, password_salt, password_hash, created_at, role) VALUES (?, ?, ?, ?, ?, ?, ?)"
  ).run(id, username, username.toLowerCase() + "@test.invalid", "salt", "hash", now, role);
  const nowSeconds = Math.floor(Date.now() / 1000);
  db.prepare(
    "INSERT INTO sessions (id, user_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?)"
  ).run("session-" + username, id, createHash("sha256").update(token).digest("hex"), nowSeconds, nowSeconds + 3600);
}

async function call(env, token, path, options = {}) {
  const headers = new Headers({
    Origin: options.origin || "https://voidcraft.test",
    Cookie: "__Host-vc_session=" + token,
    Accept: "application/json"
  });
  if (options.body !== undefined) headers.set("Content-Type", "application/json");
  const request = new Request("https://voidcraft.test" + path, {
    method: options.method || "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });
  const response = await onRequest({ request, env });
  return { response, data: await response.json() };
}

function fixture() {
  const { db, d1 } = makeDatabase();
  const env = { VOIDCRAFT_AUTH: d1 };
  const aliceId = "00000000-0000-4000-8000-000000000001";
  const bobId = "00000000-0000-4000-8000-000000000002";
  const ownerId = "00000000-0000-4000-8000-000000000003";
  seedUser(db, { id: aliceId, username: "AliceVC", token: "a".repeat(64) });
  seedUser(db, { id: bobId, username: "BobVC", token: "b".repeat(64) });
  seedUser(db, { id: ownerId, username: "OwnerVC", role: "owner", token: "c".repeat(64) });
  return { db, env, aliceId, bobId, ownerId, aliceToken: "a".repeat(64), bobToken: "b".repeat(64), ownerToken: "c".repeat(64) };
}

async function callAnonymous(env, path) {
  return onRequest({ request: new Request("https://voidcraft.test" + path), env });
}

test("existing website sessions power search, friend approval, private chat, blocks, and owner moderation", async () => {
  const f = fixture();
  try {
    const me = await call(f.env, f.aliceToken, "/api/chat/me");
    assert.equal(me.response.status, 200);
    assert.equal(me.data.user.username, "AliceVC");
    assert.equal(me.data.user.id, f.aliceId);

    const search = await call(f.env, f.aliceToken, "/api/chat/players?q=Bob");
    assert.equal(search.response.status, 200);
    assert.equal(search.data.players[0].username, "BobVC");

    const crossOrigin = await call(f.env, f.aliceToken, "/api/chat/requests", {
      method: "POST", origin: "https://attacker.invalid", body: { username: "BobVC" }
    });
    assert.equal(crossOrigin.response.status, 403);

    const sendRequest = await call(f.env, f.aliceToken, "/api/chat/requests", {
      method: "POST", body: { username: "BobVC" }
    });
    assert.equal(sendRequest.response.status, 201);
    const requestRow = f.db.prepare(
      "SELECT id FROM social_friend_requests WHERE sender_id = ? AND recipient_id = ? AND status = 'pending'"
    ).get(f.aliceId, f.bobId);
    assert.ok(requestRow);

    const incoming = await call(f.env, f.bobToken, "/api/chat/requests");
    assert.equal(incoming.response.status, 200);
    assert.equal(incoming.data.requests[0].direction, "incoming");
    assert.equal(incoming.data.requests[0].username, "AliceVC");

    const accept = await call(f.env, f.bobToken, "/api/chat/requests/" + requestRow.id + "/accept", {
      method: "POST", body: {}
    });
    assert.equal(accept.response.status, 200);

    const sent = await call(f.env, f.aliceToken, "/api/chat/conversations/" + f.bobId + "/messages", {
      method: "POST", body: { content: "Hello from the website." }
    });
    assert.equal(sent.response.status, 201);
    assert.equal(sent.data.message.body, "Hello from the website.");

    const received = await call(f.env, f.bobToken, "/api/chat/conversations/" + f.aliceId + "/messages");
    assert.equal(received.response.status, 200);
    assert.equal(received.data.messages.length, 1);
    assert.equal(received.data.messages[0].senderUsername, "AliceVC");
    assert.equal(received.data.messages[0].body, "Hello from the website.");

    const conversations = await call(f.env, f.bobToken, "/api/chat/conversations");
    assert.equal(conversations.response.status, 200);
    assert.equal(conversations.data.conversations[0].username, "AliceVC");

    const report = await call(f.env, f.aliceToken, "/api/chat/reports", {
      method: "POST",
      body: { targetUserId: f.bobId, category: "other", details: "Integration test report." }
    });
    assert.equal(report.response.status, 201);

    const queue = await call(f.env, f.ownerToken, "/api/chat/moderation/reports?status=open");
    assert.equal(queue.response.status, 200);
    assert.equal(queue.data.reports.length, 1);
    const reportId = queue.data.reports[0].id;
    const update = await call(f.env, f.ownerToken, "/api/chat/moderation/reports/" + reportId, {
      method: "PATCH", body: { status: "reviewing" }
    });
    assert.equal(update.response.status, 200);
    const reviewing = await call(f.env, f.ownerToken, "/api/chat/moderation/reports?status=reviewing");
    assert.equal(reviewing.data.reports.length, 1);

    const block = await call(f.env, f.aliceToken, "/api/chat/blocks/" + f.bobId, {
      method: "POST", body: {}
    });
    assert.equal(block.response.status, 200);
    const blockedMessage = await call(f.env, f.bobToken, "/api/chat/conversations/" + f.aliceId + "/messages");
    assert.equal(blockedMessage.response.status, 403);

    const anonymous = await callAnonymous(f.env, "/api/chat/me");
    assert.equal(anonymous.status, 401);
  } finally {
    f.db.close();
  }
});
