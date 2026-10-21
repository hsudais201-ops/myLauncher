import { currentUser, json, readJson, sameOrigin } from "../auth/_utils.js";

export async function onRequestPost({ request, env }) {
  if (!sameOrigin(request)) return json({ error: "Request origin could not be verified. Reload and retry.", code: "DEV_REVIEW_ORIGIN_REJECTED" }, 403);
  if (!env?.VOIDCRAFT_AUTH) return json({ error: "Account service is not connected.", code: "AUTH_DB_NOT_BOUND" }, 503);
  try {
    const user = await currentUser(request, env);
    if (!user) return json({ error: "Sign in to review submissions.", code: "AUTH_REQUIRED" }, 401);
    if (user.role !== "owner") return json({ error: "Only the VoidCraft owner can approve or reject store submissions.", code: "DEV_OWNER_REQUIRED" }, 403);

    const body = await readJson(request);
    const id = typeof body?.id === "string" ? body.id.trim() : "";
    const action = body?.action === "publish" ? "publish" : body?.action === "reject" ? "reject" : "";
    const note = typeof body?.note === "string" ? body.note.trim().slice(0, 500) : "";
    if (!id || id.length > 80 || !action) return json({ error: "Choose a valid submission and review action.", code: "DEV_REVIEW_INPUT_INVALID" }, 400);

    const item = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT id, package_url FROM store_items WHERE id = ? AND status = 'pending' LIMIT 1"
    ).bind(id).first();
    if (!item) return json({ error: "This pending submission was not found. Refresh the review queue.", code: "DEV_SUBMISSION_NOT_PENDING" }, 404);

    const now = new Date().toISOString();
    const status = action === "publish" ? "published" : "rejected";
    const availability = action === "publish" && item.package_url ? "available" : "preview";
    await env.VOIDCRAFT_AUTH.prepare(
      "UPDATE store_items SET status = ?, availability = ?, reviewed_at = ?, reviewer_user_id = ?, review_note = ?, updated_at = ? " +
      "WHERE id = ? AND status = 'pending'"
    ).bind(status, availability, now, user.id, note || null, now, id).run();

    return json({
      message: action === "publish" ? "Submission published in the online catalog." : "Submission rejected and kept out of the public catalog.",
      item: { id, status, availability, reviewedAt: now, reviewNote: note || null }
    }, 200);
  } catch (error) {
    console.error("VoidCraft developer review failed", { name: error?.name || "Error" });
    return json({ error: "The review action could not be completed.", code: "DEV_REVIEW_FAILED" }, 503);
  }
}
