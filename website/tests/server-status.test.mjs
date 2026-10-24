import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(new URL("../functions/api/server-status.js", import.meta.url), "utf8");
const { onRequest } = await import("data:text/javascript;base64," + Buffer.from(source).toString("base64"));
const baseUrl = "https://voidcraft-launcher.pages.dev/api/server-status";

async function withFetch(mockFetch, callback) {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = mockFetch;
  try {
    await callback();
  } finally {
    globalThis.fetch = originalFetch;
  }
}

async function callStatus(query, method = "GET") {
  return onRequest({ request: new Request(baseUrl + query, { method }) });
}

test("rejects methods other than GET without contacting the provider", async () => {
  await withFetch(async () => { throw new Error("provider must not be called"); }, async () => {
    const response = await callStatus("?address=mc.example.net", "POST");
    assert.equal(response.status, 405);
    assert.match((await response.json()).error, /Only GET/);
  });
});

test("rejects invalid server addresses without contacting the provider", async () => {
  await withFetch(async () => { throw new Error("provider must not be called"); }, async () => {
    const response = await callStatus("?address=not%20a%20host");
    assert.equal(response.status, 400);
    assert.match((await response.json()).error, /valid Minecraft server/);
  });
});

test("returns Java server status from the v3 provider endpoint", async () => {
  let requestedUrl = "";
  await withFetch(async (url) => {
    requestedUrl = String(url);
    return new Response(JSON.stringify({
      online: true,
      players: { online: 5, max: 20 },
      version: "1.21.x",
      motd: { clean: ["A test server"] }
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  }, async () => {
    const response = await callStatus("?address=mc.example.net&edition=java");
    assert.equal(response.status, 200);
    assert.equal(new URL(requestedUrl).pathname, "/3/mc.example.net");
    const body = await response.json();
    assert.equal(body.online, true);
    assert.equal(body.players.online, 5);
    assert.deepEqual(body.motd.clean, ["A test server"]);
  });
});

test("uses the Bedrock v3 endpoint when Bedrock edition is selected", async () => {
  let requestedUrl = "";
  await withFetch(async (url) => {
    requestedUrl = String(url);
    return new Response(JSON.stringify({ online: false }), {
      status: 200,
      headers: { "Content-Type": "application/json" }
    });
  }, async () => {
    const response = await callStatus("?address=bedrock.example.net&edition=bedrock");
    assert.equal(response.status, 200);
    assert.equal(new URL(requestedUrl).pathname, "/bedrock/3/bedrock.example.net");
    assert.equal((await response.json()).online, false);
  });
});

test("forwards provider rate limiting as a readable 429 response", async () => {
  await withFetch(async () => new Response("rate limited", { status: 429 }), async () => {
    const response = await callStatus("?address=mc.example.net");
    assert.equal(response.status, 429);
    assert.match((await response.json()).error, /rate-limiting/);
  });
});

test("returns a server icon with its image content type", async () => {
  await withFetch(async (url) => {
    assert.equal(new URL(String(url)).pathname, "/icon/mc.example.net");
    return new Response(new Uint8Array([137, 80, 78, 71]), {
      status: 200,
      headers: { "Content-Type": "image/png" }
    });
  }, async () => {
    const response = await callStatus("?resource=icon&address=mc.example.net");
    assert.equal(response.status, 200);
    assert.match(response.headers.get("Content-Type"), /^image\/png/);
    assert.deepEqual(Array.from(new Uint8Array(await response.arrayBuffer())), [137, 80, 78, 71]);
  });
});

test("returns 504 and a clear message when the provider times out", async () => {
  const originalSetTimeout = globalThis.setTimeout;
  globalThis.setTimeout = (callback, _delay, ...args) => originalSetTimeout(callback, 10, ...args);
  try {
    await withFetch(async (_url, options) => new Promise((_resolve, reject) => {
      options.signal.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError")), { once: true });
    }), async () => {
      const response = await callStatus("?address=slow.example.net");
      assert.equal(response.status, 504);
      assert.match((await response.json()).error, /timed out/);
    });
  } finally {
    globalThis.setTimeout = originalSetTimeout;
  }
});
