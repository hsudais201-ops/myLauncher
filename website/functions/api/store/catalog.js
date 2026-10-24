import { json } from "../auth/_utils.js";

function publicItem(row, checkoutEnabled) {
  const priceCents = row.price_cents === null ? null : Number(row.price_cents);
  const packageAvailable = Boolean(row.package_url) && priceCents !== null;
  const isFreePackage = priceCents === 0;
  return {
    id: row.id,
    slug: row.slug,
    kind: row.kind,
    name: row.name,
    tagline: row.tagline,
    description: row.description,
    imageUrl: row.image_url,
    version: row.version,
    compatibility: row.compatibility,
    permissions: row.permissions,
    license: row.license,
    sourceUrl: row.source_url,
    // Never expose a paid package URL to anonymous catalog responses.
    // Paid delivery must go through a server-side entitlement/download flow.
    packageUrl: isFreePackage ? row.package_url : null,
    packageAvailable,
    priceCents,
    currency: row.currency || "USD",
    status: row.status,
    availability: row.availability,
    creatorUsername: row.creator_username || null,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    downloadCount: Number(row.download_count || 0),
    checkoutEnabled: Boolean(checkoutEnabled)
  };
}

export async function onRequestGet({ request, env }) {
  if (!env?.VOIDCRAFT_AUTH) {
    return json({ error: "Store catalog is not connected.", code: "STORE_DB_NOT_BOUND" }, 503);
  }
  const kind = new URL(request.url).searchParams.get("kind") || "";
  if (kind && !["feature", "cosmetic"].includes(kind)) {
    return json({ error: "Choose feature or cosmetic.", code: "STORE_KIND_INVALID" }, 400);
  }
  try {
    const result = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT i.id, i.slug, i.kind, i.name, i.tagline, i.description, i.image_url, i.version, " +
      "i.compatibility, i.permissions, i.license, i.source_url, i.package_url, i.price_cents, " +
      "i.currency, i.status, i.availability, i.created_at, i.updated_at, i.download_count, " +
      "u.username AS creator_username FROM store_items i " +
      "LEFT JOIN users u ON u.id = i.creator_user_id " +
      "WHERE i.status = 'published' AND (? = '' OR i.kind = ?) " +
      "ORDER BY CASE i.availability WHEN 'available' THEN 0 WHEN 'coming_soon' THEN 1 WHEN 'preview' THEN 2 ELSE 3 END, i.name COLLATE NOCASE LIMIT 100"
    ).bind(kind, kind).all();
    const checkoutEnabled = typeof env.STRIPE_SECRET_KEY === "string" && env.STRIPE_SECRET_KEY.startsWith("sk_") && typeof env.STRIPE_WEBHOOK_SECRET === "string" && env.STRIPE_WEBHOOK_SECRET.startsWith("whsec_");
    return json({ items: (result.results || []).map(row => publicItem(row, checkoutEnabled)), checkoutEnabled }, 200);
  } catch (error) {
    console.error("VoidCraft catalog read failed", { name: error?.name || "Error" });
    return json({ error: "The store catalog could not be loaded.", code: "STORE_CATALOG_READ_FAILED" }, 503);
  }
}
