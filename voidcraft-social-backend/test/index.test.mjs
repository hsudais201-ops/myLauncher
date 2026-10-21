import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { execFileSync } from "node:child_process";
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
