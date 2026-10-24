import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("../worker.js", import.meta.url), "utf8");

test("PBKDF2 calls stay within Cloudflare's 100,000-iteration per-call limit", () => {
  assert.match(source, /const PASSWORD_ITERATIONS = 100000;/);
  assert.doesNotMatch(source, /iterations:\s*120000/);
});

test("new accounts use versioned six-pass chained password hashes", () => {
  assert.match(source, /const VERSIONED_PASSWORD_PREFIX = "v2\$";/);
  assert.match(source, /const PASSWORD_HASH_PASSES = 6;/);
  assert.match(source, /VERSIONED_PASSWORD_PREFIX \+ await hashPasswordV2\(password, salt\)/);
});

test("legacy password hashes are upgraded only after verification succeeds", () => {
  assert.match(source, /const actual = usesStrongHash/);
  assert.match(source, /if \(!constantTimeEqual\(actual, expectedHash\)\)/);
  assert.match(source, /Upgrade existing single-pass hashes only after successful verification/);
});

test("diagnostics remain server-side while client errors stay generic", () => {
  assert.match(source, /console\.error\("Social Hub request failed\.",/);
  assert.match(source, /Server error\. Please try again later\./);
});
