const API_ORIGIN = "https://api.mcsrvstat.us";
const SERVER_ADDRESS_PATTERN = /^[a-zA-Z0-9.\-:[\]]+$/;

function jsonResponse(value, status, cacheControl) {
  return new Response(JSON.stringify(value), {
    status: status || 200,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": cacheControl || "no-store",
      "X-Content-Type-Options": "nosniff"
    }
  });
}

function timeoutResponse() {
  return jsonResponse({ error: "The Minecraft status provider timed out. Please retry in a moment." }, 504, "no-store");
}

function validServerAddress(value) {
  if (typeof value !== "string") return false;
  const address = value.trim();
  if (!address || address.length > 253 || /[\\s/\\\\?#@]/.test(address)) return false;
  if (!SERVER_ADDRESS_PATTERN.test(address)) return false;
  return address.replace(/^\\[|\\]$/g, "").split(":")[0].length > 0;
}

export async function onRequest({ request }) {
  if (request.method !== "GET") {
    return jsonResponse({ error: "Only GET requests are supported." }, 405, "no-store");
  }

  const requestUrl = new URL(request.url);
  const address = (requestUrl.searchParams.get("address") || "").trim();
  const edition = requestUrl.searchParams.get("edition") === "bedrock" ? "bedrock" : "java";
  const resource = requestUrl.searchParams.get("resource") === "icon" ? "icon" : "status";

  if (!validServerAddress(address)) {
    return jsonResponse({ error: "Enter a valid Minecraft server hostname or address." }, 400, "no-store");
  }

  const path = resource === "icon"
    ? "/icon/" + encodeURIComponent(address)
    : (edition === "bedrock" ? "/bedrock/3/" : "/3/") + encodeURIComponent(address);
  const upstreamUrl = new URL(path, API_ORIGIN);

  // Keep the deadline active until the response body is completely consumed.
  // fetch() resolves once headers arrive; streaming .json() or an icon body can
  // otherwise stall indefinitely after the timer has been cleared.
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 8000);
  try {
    const upstream = await fetch(upstreamUrl.toString(), {
      method: "GET",
      headers: {
        "Accept": resource === "icon" ? "image/png,image/*;q=0.8,*/*;q=0.5" : "application/json",
        "User-Agent": "VoidCraftServerStatus/1.0 (+https://voidcraft-launcher.pages.dev)"
      },
      signal: controller.signal,
      cf: {
        cacheEverything: true,
        cacheTtl: resource === "icon" ? 300 : 60
      }
    });

    if (!upstream.ok) {
      const status = upstream.status === 429 ? 429 : 502;
      const error = upstream.status === 429
        ? "The Minecraft status provider is rate-limiting checks. Wait briefly and retry."
        : "The Minecraft status provider returned HTTP " + upstream.status + ".";
      return jsonResponse({ error, upstreamStatus: upstream.status }, status, "no-store");
    }

    if (resource === "icon") {
      const contentType = upstream.headers.get("content-type") || "image/png";
      if (!contentType.toLowerCase().startsWith("image/")) {
        return jsonResponse({ error: "The provider did not return a server icon." }, 502, "no-store");
      }
      const body = await upstream.arrayBuffer();
      const headers = new Headers({
        "Content-Type": contentType,
        "Cache-Control": "public, max-age=300, stale-while-revalidate=600",
        "X-Content-Type-Options": "nosniff"
      });
      return new Response(body, { status: 200, headers });
    }

    let data;
    try {
      data = await upstream.json();
    } catch (error) {
      if (controller.signal.aborted || (error && error.name === "AbortError")) {
        return timeoutResponse();
      }
      return jsonResponse({ error: "The Minecraft status provider returned invalid JSON." }, 502, "no-store");
    }
    if (!data || typeof data.online !== "boolean") {
      return jsonResponse({ error: "The Minecraft status provider returned an unexpected response." }, 502, "no-store");
    }

    return jsonResponse(data, 200, "public, max-age=30, stale-while-revalidate=30");
  } catch (error) {
    if (controller.signal.aborted || (error && error.name === "AbortError")) {
      return timeoutResponse();
    }
    return jsonResponse({ error: "The Minecraft status provider could not be reached. Please retry later." }, 502, "no-store");
  } finally {
    clearTimeout(timeoutId);
  }
}
