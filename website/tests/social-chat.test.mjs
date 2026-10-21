import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const root = new URL("../", import.meta.url);
async function source(path) {
  return readFile(new URL(path, root), "utf8");
}

test("Chat page exposes website-account sign-in and real chat panels", async () => {
  const html = await source("chat.html");
  assert.match(html, /href="signin\.html\?next=chat\.html"/);
  assert.match(html, /id="player-search-form"/);
  assert.match(html, /id="message-form"/);
  assert.match(html, /id="request-list"/);
  assert.match(html, /href="https:\/\/discord\.gg\/FM6myW8ZFb"/);
});

test("Chat backend uses the current website session and protects writes", async () => {
  const api = await source("functions/api/chat/[[path]].js");
  assert.match(api, /currentUser\(request, env\)/);
  assert.match(api, /sameOrigin\(request\)/);
  assert.match(api, /social_friendships/);
  assert.match(api, /social_messages/);
  assert.match(api, /rateLimit\(env, "message-send"/);
  assert.match(api, /user\.role !== "owner"/);
});

test("Chat schema is attached to existing website accounts", async () => {
  const sql = await source("migrations/0005_social_chat.sql");
  assert.match(sql, /REFERENCES users\(id\)/);
  assert.match(sql, /CREATE TABLE IF NOT EXISTS social_friendships/);
  assert.match(sql, /CREATE TABLE IF NOT EXISTS social_messages/);
  assert.match(sql, /CREATE TABLE IF NOT EXISTS social_blocks/);
  assert.match(sql, /CREATE TABLE IF NOT EXISTS social_reports/);
});

test("Message UI treats message content as text, not HTML", async () => {
  const client = await source("assets/chat.js");
  assert.match(client, /textContent/);
  assert.doesNotMatch(client, /\.innerHTML\s*=/);
  assert.match(client, /credentials:\s*"same-origin"/);
  assert.doesNotMatch(client, /localStorage|sessionStorage/);
});

test("All main pages get a Chat navigation link through shared site JS", async () => {
  const app = await source("assets/app.js");
  assert.match(app, /href = "chat\.html"/);
});
