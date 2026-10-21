import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { execFileSync } from "node:child_process";
import { DatabaseSync } from "node:sqlite";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import worker from "../src/index.mjs";
import { onRequest as pagesProxy } from "../pages/functions/api/[[path]].js";

const here = dirname(fileURLToPath(import.meta.url));
const env = {
  APP_ORIGIN: "https://voidcraft-website.pages.dev",
  VOIDCRAFT_PROXY_SECRET: "test-secret-with-at-least-32-characters-long",
  DB: {
    prepare(sql) {
      return {
        bind() { return this; },
        async first() { return { ok: 1 }; },
        async all() { return { results: [] }; },
        async run() { return { success: true, meta: { changes: 0, last_row_id: 1 } }; }
      };
    },
    async batch(statements) {
      return Promise.all(statements.map(statement => statement.run()));
    }
  }
};

test("health endpoint confirms API and database binding respond", async () => {
  const response = await worker.fetch(
    new Request("https://social.example/api/health", {
      headers: { "X-VC-Proxy-Secret": env.VOIDCRAFT_PROXY_SECRET }
    }),
    env
  );
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true, service: "voidcraft-social-api" });
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.equal(response.headers.get("x-content-type-options"), "nosniff");
});



test("Worker rejects direct requests without its private proxy secret", async () => {
  const response = await worker.fetch(
    new Request("https://social.example/api/health"),
    env
  );
  assert.equal(response.status, 403);
  assert.deepEqual(await response.json(), {
    error: "Request must go through the configured VoidCraft website proxy."
  });
});

test("preflight is limited to the configured website origin", async () => {
  const response = await worker.fetch(new Request("https://social.example/api/auth/login", {
    method: "OPTIONS",
    headers: { Origin: env.APP_ORIGIN, "X-VC-Proxy-Secret": env.VOIDCRAFT_PROXY_SECRET }
  }), env);
  assert.equal(response.status, 204);
  assert.equal(response.headers.get("access-control-allow-origin"), env.APP_ORIGIN);
  assert.equal(response.headers.get("access-control-allow-credentials"), "true");
  assert.match(response.headers.get("access-control-allow-headers"), /Authorization/);
});

test("requests from a different browser origin are denied", async () => {
  const response = await worker.fetch(new Request("https://social.example/api/health", {
    headers: { Origin: "https://attacker.invalid", "X-VC-Proxy-Secret": env.VOIDCRAFT_PROXY_SECRET }
  }), env);
  assert.equal(response.status, 403);
  assert.equal(response.headers.get("access-control-allow-origin"), null);
});

test("unknown API paths return a plain not-found error", async () => {
  const response = await worker.fetch(new Request("https://social.example/api/health/extra", {
    headers: { "X-VC-Proxy-Secret": env.VOIDCRAFT_PROXY_SECRET }
  }), env);
  assert.equal(response.status, 404);
  assert.deepEqual(await response.json(), { error: "API endpoint not found." });
});

test("D1 migration defines separate accounts, session, friend, message, block, report, and rate-limit data", async () => {
  const migration = await readFile(resolve(here, "../migrations/0001_initial.sql"), "utf8");
  for (const table of [
    "users", "sessions", "friend_requests", "friendships",
    "messages", "blocks", "reports", "rate_limits"
  ]) {
    assert.ok(migration.includes("CREATE TABLE IF NOT EXISTS " + table + " ("), "missing table " + table);
  }
  assert.match(migration, /CHECK \(user_id < friend_id\)/);
  assert.match(migration, /password_hash TEXT NOT NULL/);
  assert.match(migration, /token_hash TEXT NOT NULL UNIQUE/);
  assert.match(migration, /CHECK \(sender_id <> recipient_id\)/);
  assert.doesNotMatch(migration, /PRAGMA foreign_keys/i);
});


test("Pages proxy fails closed if its Worker URL is not configured", async () => {
  const request = new Request("https://voidcraft-website.pages.dev/api/auth/me");
  const response = await pagesProxy({ request, env: {} });
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), {
    error: "VoidCraft social service is not configured on this website yet."
  });
  const noSecret = await pagesProxy({
    request,
    env: { VOIDCRAFT_SOCIAL_API_URL: "https://social.example.workers.dev" }
  });
  assert.equal(noSecret.status, 503);
});

test("Pages proxy refuses non-HTTPS or self-referential upstreams", async () => {
  const request = new Request("https://voidcraft-website.pages.dev/api/auth/me");
  const httpResponse = await pagesProxy({
    request,
    env: { VOIDCRAFT_SOCIAL_API_URL: "http://social.example.workers.dev", VOIDCRAFT_PROXY_SECRET: env.VOIDCRAFT_PROXY_SECRET }
  });
  assert.equal(httpResponse.status, 500);
  const selfResponse = await pagesProxy({
    request,
    env: { VOIDCRAFT_SOCIAL_API_URL: "https://voidcraft-website.pages.dev", VOIDCRAFT_PROXY_SECRET: env.VOIDCRAFT_PROXY_SECRET }
  });
  assert.equal(selfResponse.status, 500);
});

test("Pages proxy forwards path, query, origin and session headers without rewriting the response cookie", async () => {
  const originalFetch = globalThis.fetch;
  let capturedRequest;
  globalThis.fetch = async (request) => {
    capturedRequest = request;
    return new Response(JSON.stringify({ ok: true }), {
      status: 201,
      headers: { "Content-Type": "application/json", "Set-Cookie": "vc_session=example; Path=/; HttpOnly; Secure; SameSite=Strict" }
    });
  };
  try {
    const request = new Request(
      "https://voidcraft-website.pages.dev/api/auth/register?source=join",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Origin": "https://voidcraft-website.pages.dev",
          "Cookie": "existing=session",
          "Authorization": "Bearer native-token",
          "CF-Connecting-IP": "203.0.113.10"
        },
        body: JSON.stringify({ username: "test_user" })
      }
    );
    const response = await pagesProxy({
      request,
      env: {
        VOIDCRAFT_SOCIAL_API_URL: "https://social.example.workers.dev",
        VOIDCRAFT_PROXY_SECRET: env.VOIDCRAFT_PROXY_SECRET
      }
    });
    assert.equal(response.status, 201);
    assert.equal(new URL(capturedRequest.url).origin, "https://social.example.workers.dev");
    assert.equal(new URL(capturedRequest.url).pathname, "/api/auth/register");
    assert.equal(new URL(capturedRequest.url).search, "?source=join");
    assert.equal(capturedRequest.headers.get("origin"), "https://voidcraft-website.pages.dev");
    assert.equal(capturedRequest.headers.get("cookie"), "existing=session");
    assert.equal(capturedRequest.headers.get("authorization"), "Bearer native-token");
    assert.equal(capturedRequest.headers.get("x-vc-proxy-secret"), env.VOIDCRAFT_PROXY_SECRET);
    assert.equal(capturedRequest.headers.get("x-vc-client-ip"), "203.0.113.10");
    assert.match(response.headers.get("set-cookie"), /HttpOnly/);
    assert.deepEqual(await response.json(), { ok: true });
  } finally {
    globalThis.fetch = originalFetch;
  }
});


test("website social example script parses and renders user text without HTML injection", async () => {
  const html = await readFile(resolve(here, "../website-integration/social-page.example.html"), "utf8");
  const match = html.match(/<script type="module">([\s\S]*?)<\/script>/);
  assert.ok(match, "example page should include a module script");
  execFileSync(process.execPath, ["--input-type=module", "--check"], {
    input: match[1],
    stdio: ["pipe", "pipe", "pipe"]
  });
  assert.match(html, /textContent/);
  assert.doesNotMatch(html, /\.innerHTML\s*=/);
  assert.match(html, /api\.me\(\)|VoidCraftSocialApi/);
  assert.match(html, /not for in-app private messages/);
});



function createRealD1Fixture(migration) {
  const sqlite = new DatabaseSync(":memory:");
  sqlite.exec("PRAGMA foreign_keys = ON");
  sqlite.exec(migration);

  function prepare(sql) {
    let parameters = [];
    return {
      bind(...values) {
        parameters = values;
        return this;
      },
      async first() {
        return sqlite.prepare(sql).get(...parameters) ?? null;
      },
      async all() {
        return { results: sqlite.prepare(sql).all(...parameters) };
      },
      async run() {
        const result = sqlite.prepare(sql).run(...parameters);
        return {
          success: true,
          meta: {
            changes: Number(result.changes),
            last_row_id: Number(result.lastInsertRowid)
          }
        };
      }
    };
  }

  const env = {
    APP_ORIGIN: "https://voidcraft-website.pages.dev",
    VOIDCRAFT_PROXY_SECRET: "test-secret-with-at-least-32-characters-long",
    DB: {
      prepare,
      async batch(statements) {
        sqlite.exec("BEGIN");
        try {
          const results = [];
          for (const statement of statements) results.push(await statement.run());
          sqlite.exec("COMMIT");
          return results;
        } catch (error) {
          sqlite.exec("ROLLBACK");
          throw error;
        }
      }
    }
  };
  return { env, sqlite };
}

test("website and launcher accounts share real friend requests, private messages, and blocks", async () => {
  const migration = await readFile(resolve(here, "../migrations/0001_initial.sql"), "utf8");
  const fixture = createRealD1Fixture(migration);
  const env = fixture.env;
  const secret = env.VOIDCRAFT_PROXY_SECRET;
  const request = async (path, options = {}) => {
    const headers = new Headers({ "X-VC-Proxy-Secret": secret });
    if (options.origin !== false) headers.set("Origin", env.APP_ORIGIN);
    if (options.cookie) headers.set("Cookie", options.cookie);
    if (options.token) headers.set("Authorization", "Bearer " + options.token);
    if (options.body !== undefined) headers.set("Content-Type", "application/json");
    return worker.fetch(new Request("https://social.example/api" + path, {
      method: options.method || "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body)
    }), env);
  };

  try {
    const registration = await request("/auth/register", {
      method: "POST",
      body: {
        email: "mflegend@example.com",
        username: "mflegend",
        password: "a-long-unique-test-password"
      }
    });
    assert.equal(registration.status, 201);
    const firstCookie = registration.headers.get("set-cookie").split(";")[0];
    const firstAccount = (await registration.json()).user;
    assert.equal(firstAccount.username, "mflegend");
    assert.equal(firstAccount.role, "member", "username alone must not grant owner status");

    const stored = fixture.sqlite.prepare(
      "SELECT password_hash, password_salt FROM users WHERE id = ?"
    ).get(firstAccount.id);
    assert.notEqual(stored.password_hash, "a-long-unique-test-password");
    assert.ok(stored.password_salt.length > 10);

    const secondRegistration = await request("/launcher/auth/register", {
      method: "POST",
      origin: false,
      body: {
        email: "friend@example.com",
        username: "friend_test",
        password: "another-long-test-password"
      }
    });
    assert.equal(secondRegistration.status, 201);
    const second = await secondRegistration.json();
    const secondToken = second.accessToken;
    const secondId = second.user.id;

    const crossClientMe = await request("/auth/me", { token: secondToken });
    assert.equal(crossClientMe.status, 200);
    assert.equal((await crossClientMe.json()).user.username, "friend_test");

    const sendRequest = await request("/friends/requests", {
      method: "POST",
      cookie: firstCookie,
      body: { username: "friend_test" }
    });
    assert.equal(sendRequest.status, 201);
    const pending = await request("/friends/requests", { token: secondToken });
    const pendingJson = await pending.json();
    assert.equal(pendingJson.requests.length, 1);
    assert.equal(pendingJson.requests[0].direction, "incoming");
    const requestId = pendingJson.requests[0].id;

    const accepted = await request("/friends/requests/" + requestId + "/accept", {
      method: "POST",
      token: secondToken,
      body: {}
    });
    assert.equal(accepted.status, 200);

    const list = await request("/friends", { cookie: firstCookie });
    const firstFriends = (await list.json()).friends;
    assert.equal(firstFriends.length, 1);
    assert.equal(firstFriends[0].username, "friend_test");

    const sent = await request("/conversations/" + secondId + "/messages", {
      method: "POST",
      cookie: firstCookie,
      body: { content: "Hello from VoidCraft website" }
    });
    assert.equal(sent.status, 201);
    const loaded = await request("/conversations/" + firstAccount.id + "/messages", {
      token: secondToken
    });
    assert.equal(loaded.status, 200);
    const messages = (await loaded.json()).messages;
    assert.equal(messages.length, 1);
    assert.equal(messages[0].body, "Hello from VoidCraft website");
    assert.equal(messages[0].senderUsername, "mflegend");

    const recent = await request("/conversations", { token: secondToken });
    assert.equal((await recent.json()).conversations.length, 1);

    const blocked = await request("/blocks/" + firstAccount.id, {
      method: "POST",
      token: secondToken,
      body: {}
    });
    assert.equal(blocked.status, 200);
    const blockedMessage = await request("/conversations/" + secondId + "/messages", {
      method: "POST",
      cookie: firstCookie,
      body: { content: "This must not be delivered" }
    });
    assert.equal(blockedMessage.status, 403);

    const reportCreated = await request("/reports", {
      method: "POST",
      cookie: firstCookie,
      body: {
        targetUserId: secondId,
        category: "spam",
        details: "Automated test report for moderation queue."
      }
    });
    assert.equal(reportCreated.status, 201);
    const reportId = (await reportCreated.json()).reportId;

    const deniedQueue = await request("/moderation/reports", { token: secondToken });
    assert.equal(deniedQueue.status, 403, "ordinary members must not read the moderation queue");

    fixture.sqlite.prepare("UPDATE users SET role = 'moderator' WHERE id = ?").run(secondId);
    const queue = await request("/moderation/reports?status=open", { token: secondToken });
    assert.equal(queue.status, 200);
    const queuedReports = (await queue.json()).reports;
    assert.equal(queuedReports.length, 1);
    assert.equal(queuedReports[0].id, reportId);
    assert.equal(queuedReports[0].reporterUsername, "mflegend");
    assert.equal(queuedReports[0].targetUsername, "friend_test");

    const invalidStatus = await request("/moderation/reports/" + reportId, {
      method: "PATCH",
      token: secondToken,
      body: { status: "delete-user" }
    });
    assert.equal(invalidStatus.status, 400);

    const resolved = await request("/moderation/reports/" + reportId, {
      method: "PATCH",
      token: secondToken,
      body: { status: "resolved" }
    });
    assert.equal(resolved.status, 200);
    assert.equal((await resolved.json()).status, "resolved");
    const resolvedQueue = await request("/moderation/reports?status=resolved", { token: secondToken });
    assert.equal((await resolvedQueue.json()).reports.length, 1);

    const logout = await request("/auth/logout", {
      method: "POST",
      cookie: firstCookie,
      body: {}
    });
    assert.equal(logout.status, 200);
    const afterLogout = await request("/auth/me", { cookie: firstCookie });
    assert.equal(afterLogout.status, 401);
  } finally {
    fixture.sqlite.close();
  }
});
