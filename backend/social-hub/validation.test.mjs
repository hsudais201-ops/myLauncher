import test from "node:test";
import assert from "node:assert/strict";
import { normalizeUsername } from "./validation.mjs";

test("accepts and trims a valid 3-character username", () => {
  assert.equal(normalizeUsername("  abc  "), "abc");
});

test("accepts a username at the 24-character limit", () => {
  assert.equal(normalizeUsername("a".repeat(24)), "a".repeat(24));
});

test("rejects longer usernames instead of silently truncating them", () => {
  assert.equal(normalizeUsername("a".repeat(25)), null);
});

test("rejects usernames shorter than three characters", () => {
  assert.equal(normalizeUsername("ab"), null);
  assert.equal(normalizeUsername(""), null);
});

test("rejects characters outside letters, numbers and underscore", () => {
  assert.equal(normalizeUsername("has-dash"), null);
  assert.equal(normalizeUsername("has space"), null);
  assert.equal(normalizeUsername("with.dot"), null);
});

test("rejects non-string input", () => {
  assert.equal(normalizeUsername(null), null);
  assert.equal(normalizeUsername(123), null);
  assert.equal(normalizeUsername({ username: "valid" }), null);
});
