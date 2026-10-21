import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import worker from "../src/index.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const env = {
  APP_ORIGIN: "https://voidcraft-website.pages.dev",
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
    new Request("https://social.example/api/health"),
    env
  );
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true, service: "voidcraft-social-api" });
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.equal(response.headers.get("x-content-type-options"), "nosniff");
});

test("preflight is limited to the configured website origin", async () => {
  const response = await worker.fetch(new Request("https://social.example/api/auth/login", {
    method: "OPTIONS",
    headers: { Origin: env.APP_ORIGIN }
  }), env);
  assert.equal(response.status, 204);
  assert.equal(response.headers.get("access-control-allow-origin"), env.APP_ORIGIN);
  assert.equal(response.headers.get("access-control-allow-credentials"), "true");
  assert.match(response.headers.get("access-control-allow-headers"), /Authorization/);
});

test("requests from a different browser origin are denied", async () => {
  const response = await worker.fetch(new Request("https://social.example/api/health", {
    headers: { Origin: "https://attacker.invalid" }
  }), env);
  assert.equal(response.status, 403);
  assert.equal(response.headers.get("access-control-allow-origin"), null);
});

test("unknown API paths return a plain not-found error", async () => {
  const response = await worker.fetch(new Request("https://social.example/api/health/extra"), env);
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
});
