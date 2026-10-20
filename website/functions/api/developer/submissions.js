import { allowAttempt, currentUser, json, readJson, sameOrigin } from "../auth/_utils.js";

function validHttpsUrl(value, required = false) {
  if (typeof value !== "string" || !value.trim()) return !required;
  if (value.length > 1000) return false;
  try {
    const url = new URL(value.trim());
    return url.protocol === "https:" && Boolean(url.hostname) && !url.username && !url.password;
  } catch {
    return false;
  }
}

function validImageUrl(value) {
  if (typeof value !== "string" || !value.trim()) return true;
  if (value.length > 1000) return false;
  if (/^(assets\/|\/assets\/)/.test(value.trim())) return true;
  return validHttpsUrl(value, true);
}

function displayItem(row) {
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
    packageUrl: row.package_url,
    priceCents: row.price_cents === null ? null : Number(row.price_cents),
    currency: row.currency || "USD",
    status: row.status,
    availability: row.availability,
    creatorUsername: row.creator_username || null,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    reviewedAt: row.reviewed_at,
    reviewNote: row.review_note,
    downloadCount: Number(row.download_count || 0)
  };
}

export async function onRequestGet({ request, env }) {
  if (!env?.VOIDCRAFT_AUTH) return json({ error: "Account service is not connected.", code: "AUTH_DB_NOT_BOUND" }, 503);
  try {
    const user = await currentUser(request, env);
    if (!user) return json({ error: "Sign in to open the developer portal.", code: "AUTH_REQUIRED" }, 401);
    const result = user.role === "owner"
      ? await env.VOIDCRAFT_AUTH.prepare(
          "SELECT i.*, u.username AS creator_username FROM store_items i " +
          "LEFT JOIN users u ON u.id = i.creator_user_id " +
          "ORDER BY CASE i.status WHEN 'pending' THEN 0 ELSE 1 END, i.created_at DESC LIMIT 100"
        ).all()
      : await env.VOIDCRAFT_AUTH.prepare(
          "SELECT i.*, u.username AS creator_username FROM store_items i " +
          "LEFT JOIN users u ON u.id = i.creator_user_id " +
          "WHERE i.creator_user_id = ? ORDER BY i.created_at DESC LIMIT 100"
        ).bind(user.id).all();
    return json({
      user: { username: user.username, role: user.role === "owner" ? "owner" : "member" },
      items: (result.results || []).map(displayItem)
    }, 200);
  } catch (error) {
    console.error("VoidCraft developer submissions read failed", { name: error?.name || "Error" });
    return json({ error: "Your submissions could not be loaded.", code: "DEV_SUBMISSIONS_READ_FAILED" }, 503);
  }
}

export async function onRequestPost({ request, env }) {
  if (!sameOrigin(request)) return json({ error: "Request origin could not be verified. Reload and retry.", code: "DEV_ORIGIN_REJECTED" }, 403);
  if (!env?.VOIDCRAFT_AUTH) return json({ error: "Account service is not connected.", code: "AUTH_DB_NOT_BOUND" }, 503);
  try {
    const user = await currentUser(request, env);
    if (!user) return json({ error: "Sign in before submitting a store item.", code: "AUTH_REQUIRED" }, 401);
    const limit = await allowAttempt(request, env, "developer-submission");
    if (!limit.allowed) return json({ error: "Too many submissions in a short period. Please try again later.", code: "DEV_SUBMISSION_RATE_LIMITED" }, 429, { "Retry-After": String(limit.retryAfter) });
    const body = await readJson(request);
    if (!body) return json({ error: "The submission form could not be read.", code: "DEV_INVALID_JSON" }, 400);

    const kind = body.kind === "cosmetic" ? "cosmetic" : body.kind === "feature" ? "feature" : "";
    const name = typeof body.name === "string" ? body.name.trim() : "";
    const tagline = typeof body.tagline === "string" ? body.tagline.trim() : "";
    const description = typeof body.description === "string" ? body.description.trim() : "";
    const sourceUrl = typeof body.sourceUrl === "string" ? body.sourceUrl.trim() : "";
    const packageUrl = typeof body.packageUrl === "string" ? body.packageUrl.trim() : "";
    const imageUrl = typeof body.imageUrl === "string" ? body.imageUrl.trim() : "";
    const license = typeof body.license === "string" ? body.license.trim() : "";
    const version = typeof body.version === "string" ? body.version.trim() : "";
    const compatibility = typeof body.compatibility === "string" ? body.compatibility.trim() : "";
    const permissions = typeof body.permissions === "string" ? body.permissions.trim() : "";
    const pricingMode = body.pricingMode === "paid" ? "paid" : body.pricingMode === "free" ? "free" : "";
    const priceCents = pricingMode === "paid" ? Number(body.priceCents) : 0;

    if (!kind) return json({ error: "Choose Feature or Cosmetic.", code: "DEV_KIND_INVALID" }, 400);
    if (name.length < 3 || name.length > 60) return json({ error: "Name must be 3–60 characters.", code: "DEV_NAME_INVALID" }, 400);
    if (!tagline || tagline.length > 120) return json({ error: "Add a short description of up to 120 characters.", code: "DEV_TAGLINE_INVALID" }, 400);
    if (description.length < 20 || description.length > 1800) return json({ error: "Description must be 20–1,800 characters.", code: "DEV_DESCRIPTION_INVALID" }, 400);
    if (!validHttpsUrl(sourceUrl, true)) return json({ error: "Source repository must be a valid HTTPS URL.", code: "DEV_SOURCE_URL_INVALID" }, 400);
    if (!validHttpsUrl(packageUrl, false)) return json({ error: "Package URL must be a public HTTPS URL (or leave it blank).", code: "DEV_PACKAGE_URL_INVALID" }, 400);
    if (!validImageUrl(imageUrl)) return json({ error: "Preview image must use HTTPS or a local assets/ path.", code: "DEV_IMAGE_URL_INVALID" }, 400);
    if (!license || license.length > 100) return json({ error: "Provide the package license or write 'Proprietary — owned by me' if applicable.", code: "DEV_LICENSE_INVALID" }, 400);
    if (!version || version.length > 80) return json({ error: "Provide a version or draft identifier.", code: "DEV_VERSION_INVALID" }, 400);
    if (!compatibility || compatibility.length > 240) return json({ error: "Describe supported launcher/device versions.", code: "DEV_COMPATIBILITY_INVALID" }, 400);
    if (!permissions || permissions.length > 500) return json({ error: "Declare requested permissions and network access.", code: "DEV_PERMISSIONS_INVALID" }, 400);
    if (!pricingMode) return json({ error: "Choose free or paid pricing.", code: "DEV_PRICING_INVALID" }, 400);
    if (pricingMode === "paid" && (!Number.isSafeInteger(priceCents) || priceCents < 50 || priceCents > 10000000)) {
      return json({ error: "Enter a paid price between $0.50 and $100,000.00 USD.", code: "DEV_PRICE_INVALID" }, 400);
    }

    const id = crypto.randomUUID();
    const baseSlug = name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 48) || "store-item";
    const slug = baseSlug + "-" + id.slice(0, 8);
    const now = new Date().toISOString();
    await env.VOIDCRAFT_AUTH.prepare(
      "INSERT INTO store_items (id, slug, kind, name, tagline, description, image_url, version, compatibility, permissions, license, source_url, package_url, price_cents, currency, status, availability, creator_user_id, created_at, updated_at) " +
      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'USD', 'pending', 'preview', ?, ?, ?)"
    ).bind(
      id, slug, kind, name, tagline, description, imageUrl || (kind === "cosmetic" ? "assets/previews/creator-packs.svg" : "assets/previews/performance-advisor.svg"),
      version, compatibility, permissions, license, sourceUrl, packageUrl || null, priceCents, user.id, now, now
    ).run();
    return json({
      message: "Submission received. It is private until the owner reviews it.",
      item: { id, slug, kind, name, status: "pending", availability: "preview", priceCents }
    }, 201);
  } catch (error) {
    console.error("VoidCraft developer submission failed", { name: error?.name || "Error" });
    return json({ error: "The submission could not be saved. Please try again.", code: "DEV_SUBMISSION_SAVE_FAILED" }, 503);
  }
}
