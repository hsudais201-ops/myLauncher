import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(new URL("../functions/social-api/[[catchall]].js", import.meta.url), "utf8");
const { onRequest } = await import("data:text/javascript;base64," + Buffer.from(source).toString("base64"));

async function withFetch(mockFetch, callback) {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = mockFetch;
  try {
    await callback();
  } finally {
    globalThis.fetch = originalFetch;
  }
}

test("forwards the Social Hub API path and disables response caching", async () => {
  await withFetch(async (upstreamRequest) => {
    assert.equal(upstreamRequest.url, "https://voidcraft-social-hub-staging.hsudais201.workers.dev/api/health?check=1");
    assert.equal(upstreamRequest.headers.get("origin"), null);
    assert.equal(upstreamRequest.headers.get("authorization"), "Bearer test-token");
    return Response.json({ status: "ok" }, {
      headers: {
        "cache-control": "public, max-age=60",
        "access-control-allow-origin": "*",
        "x-service-test": "present"
      }
    });
  }, async () => {
    const request = new Request("https://voidcraft-launcher.pages.dev/social-api/api/health?check=1", {
      headers: { "Origin": "https://voidcraft-launcher.pages.dev", "Authorization": "Bearer test-token" }
    });
    const response = await onRequest({ request });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("cache-control"), "no-store");
    assert.equal(response.headers.get("access-control-allow-origin"), null);
    assert.equal(response.headers.get("x-service-test"), "present");
    assert.deepEqual(await response.json(), { status: "ok" });
  });
});

test("returns a readable 504 when the Social Hub Worker times out", async () => {
  const originalSetTimeout = globalThis.setTimeout;
  globalThis.setTimeout = (callback, _delay, ...args) => originalSetTimeout(callback, 10, ...args);
  try {
    await withFetch(async (upstreamRequest) => new Promise((_resolve, reject) => {
      upstreamRequest.signal.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError")), { once: true });
    }), async () => {
      const request = new Request("https://voidcraft-launcher.pages.dev/social-api/api/messages");
      const response = await onRequest({ request });
      assert.equal(response.status, 504);
      assert.match((await response.json()).error, /timed out/);
      assert.equal(response.headers.get("cache-control"), "no-store");
    });
  } finally {
    globalThis.setTimeout = originalSetTimeout;
  }
});

test("returns a readable 504 when the Social Hub Worker response body stalls", async () => {
  const originalSetTimeout = globalThis.setTimeout;
  globalThis.setTimeout = (callback, _delay, ...args) => originalSetTimeout(callback, 10, ...args);
  try {
    await withFetch(async (upstreamRequest) => new Response(new ReadableStream({
      start(controller) {
        const abort = () => controller.error(new DOMException("Aborted", "AbortError"));
        if (upstreamRequest.signal.aborted) abort();
        else upstreamRequest.signal.addEventListener("abort", abort, { once: true });
      }
    }), {
      status: 200,
      headers: { "content-type": "application/json" }
    }), async () => {
      const request = new Request("https://voidcraft-launcher.pages.dev/social-api/api/messages");
      const response = await onRequest({ request });
      assert.equal(response.status, 504);
      assert.match((await response.json()).error, /timed out/);
      assert.equal(response.headers.get("cache-control"), "no-store");
    });
  } finally {
    globalThis.setTimeout = originalSetTimeout;
  }
});
