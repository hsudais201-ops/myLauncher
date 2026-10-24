const UPSTREAM = "https://voidcraft-social-hub-staging.hsudais201.workers.dev";

export async function onRequest(context) {
  const { request } = context;
  const incoming = new URL(request.url);
  const target = new URL(UPSTREAM);
  target.pathname = incoming.pathname.replace(/^\/social-api/, "") || "/";
  target.search = incoming.search;

  const headers = new Headers(request.headers);
  headers.delete("host");
  headers.delete("origin");

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 10000);
  try {
    const init = {
      method: request.method,
      headers,
      redirect: "manual",
      signal: controller.signal,
    };
    if (request.method !== "GET" && request.method !== "HEAD") {
      init.body = request.body;
    }
    const upstream = await fetch(new Request(target.toString(), init));
    const responseHeaders = new Headers(upstream.headers);
    responseHeaders.set("cache-control", "no-store");
    responseHeaders.delete("access-control-allow-origin");
    responseHeaders.delete("access-control-allow-methods");
    responseHeaders.delete("access-control-allow-headers");
    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: responseHeaders,
    });
  } catch (error) {
    const timedOut = error && error.name === "AbortError";
    return Response.json(
      { error: timedOut
        ? "Social Hub request timed out. Please retry in a moment."
        : "Social Hub service is temporarily unreachable. Please try again shortly." },
      { status: timedOut ? 504 : 502, headers: { "cache-control": "no-store" } },
    );
  } finally {
    clearTimeout(timeoutId);
  }
}
