/**
 * Cloudflare Pages catch-all proxy for the VoidCraft social API.
 *
 * Copy this file to functions/api/[[path]].js in the website's Pages repository.
 * Set VOIDCRAFT_SOCIAL_API_URL to the origin of the deployed Worker, for example
 * https://voidcraft-social-backend.<your-workers-subdomain>.workers.dev
 *
 * Keeping this proxy on the website origin lets browser session cookies remain
 * host-scoped to voidcraft-website.pages.dev; the Worker sees the original
 * Origin, Cookie, and Authorization headers but never stores passwords in URLs.
 */
export async function onRequest(context) {
  const configured = context.env.VOIDCRAFT_SOCIAL_API_URL;
  if (!configured) {
    return Response.json(
      { error: "VoidCraft social service is not configured on this website yet." },
      {
        status: 503,
        headers: {
          "Cache-Control": "no-store",
          "X-Content-Type-Options": "nosniff"
        }
      }
    );
  }

  let upstream;
  try {
    upstream = new URL(configured);
  } catch {
    return Response.json({ error: "Invalid social service configuration." }, { status: 500 });
  }
  if (
    upstream.protocol !== "https:" ||
    upstream.username ||
    upstream.password ||
    upstream.pathname !== "/" ||
    upstream.search ||
    upstream.hash ||
    upstream.hostname === "voidcraft-website.pages.dev"
  ) {
    return Response.json({ error: "Invalid social service configuration." }, { status: 500 });
  }

  const target = new URL(context.request.url);
  target.protocol = upstream.protocol;
  target.host = upstream.host;

  try {
    const response = await fetch(new Request(target.toString(), context.request), {
      redirect: "manual"
    });
    const headers = new Headers(response.headers);
    headers.set("Cache-Control", "no-store");
    headers.set("X-Content-Type-Options", "nosniff");
    headers.set("Referrer-Policy", "no-referrer");
    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers
    });
  } catch {
    return Response.json(
      { error: "VoidCraft account service is temporarily unreachable." },
      {
        status: 502,
        headers: {
          "Cache-Control": "no-store",
          "X-Content-Type-Options": "nosniff"
        }
      }
    );
  }
}
