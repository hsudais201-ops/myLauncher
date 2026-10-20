import { allowAttempt, json, randomHex, readJson, sameOrigin } from "../_utils.js";

const CODE_TTL_SECONDS = 10 * 60;

async function codeHash(secret, resetId, code) {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const digest = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(resetId + ":" + code)
  );
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

export async function onRequestPost({ request, env }) {
  if (!sameOrigin(request)) {
    return json({ error: "Request origin could not be verified. Reload and try again.", code: "RESET_ORIGIN_REJECTED" }, 403);
  }
  if (!env?.VOIDCRAFT_AUTH) {
    return json({ error: "The account service is not connected.", code: "AUTH_DB_NOT_BOUND" }, 503);
  }

  const limit = await allowAttempt(request, env, "password-reset-request");
  if (!limit.allowed) {
    return json({ error: "Too many reset requests. Please try again later.", code: "RESET_RATE_LIMITED" }, 429, { "Retry-After": String(limit.retryAfter) });
  }

  // Email sending requires a verified sending domain plus a narrowly scoped API token.
  // Never generate or reveal a reset code when the delivery provider is not configured.
  const accountId = env.CF_ACCOUNT_ID || "f0de78f2f59b81e2c859a732f7bcf320";
  const apiToken = env.CF_EMAIL_API_TOKEN;
  const fromAddress = env.CF_EMAIL_FROM;
  const resetSecret = env.PASSWORD_RESET_SECRET;
  if (!apiToken || !fromAddress || typeof resetSecret !== "string" || resetSecret.length < 32) {
    return json({
      error: "Password-reset email delivery is not configured yet. The site owner must connect a verified sending domain and the required server secrets before codes can be delivered.",
      code: "RESET_EMAIL_NOT_CONFIGURED"
    }, 503);
  }

  try {
    const body = await readJson(request);
    const email = typeof body?.email === "string" ? body.email.trim().toLowerCase() : "";
    if (!email || email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      return json({ error: "Enter the email address connected to your VoidCraft account.", code: "RESET_EMAIL_INVALID" }, 400);
    }

    const user = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT id, email FROM users WHERE email = ? COLLATE NOCASE LIMIT 1"
    ).bind(email).first();

    // Use the same public response for existing and unknown accounts to reduce account enumeration.
    if (!user) {
      return json({ message: "If that email belongs to a VoidCraft account, a reset code will be sent shortly." }, 200);
    }

    const now = Math.floor(Date.now() / 1000);
    const resetId = crypto.randomUUID();
    const code = String(crypto.getRandomValues(new Uint32Array(1))[0] % 1000000).padStart(6, "0");
    const digest = await codeHash(resetSecret, resetId, code);

    await env.VOIDCRAFT_AUTH.prepare(
      "UPDATE password_reset_codes SET used_at = ? WHERE user_id = ? AND used_at IS NULL"
    ).bind(now, user.id).run();
    await env.VOIDCRAFT_AUTH.prepare(
      "DELETE FROM password_reset_codes WHERE expires_at <= ? OR used_at IS NOT NULL"
    ).bind(now - 24 * 60 * 60).run();
    await env.VOIDCRAFT_AUTH.prepare(
      "INSERT INTO password_reset_codes (id, user_id, code_hash, created_at, expires_at, attempts) VALUES (?, ?, ?, ?, ?, 0)"
    ).bind(resetId, user.id, digest, now, now + CODE_TTL_SECONDS).run();

    const response = await fetch("https://api.cloudflare.com/client/v4/accounts/" + encodeURIComponent(accountId) + "/email/sending/send", {
      method: "POST",
      headers: {
        "Authorization": "Bearer " + apiToken,
        "Content-Type": "application/json",
        "Accept": "application/json"
      },
      body: JSON.stringify({
        to: user.email,
        from: { address: fromAddress, name: "VoidCraft" },
        subject: "Your VoidCraft password reset code",
        text: "Use this six-digit code to reset your VoidCraft website password: " + code + "\n\nThe code expires in 10 minutes. If you did not request it, you can ignore this email.",
        html: "<div style=\"font-family:Arial,sans-serif;max-width:520px;margin:auto;color:#171827\"><h1>VoidCraft password reset</h1><p>Use this code to set a new password for your website account:</p><p style=\"font-size:30px;font-weight:800;letter-spacing:8px;padding:16px 18px;background:#f0edff;border-radius:12px;display:inline-block\">" + code + "</p><p>This code expires in 10 minutes and can be used once. If you did not request it, ignore this message.</p></div>"
      })
    });

    const delivery = await response.json().catch(() => ({}));
    if (!response.ok || delivery.success === false) {
      await env.VOIDCRAFT_AUTH.prepare(
        "UPDATE password_reset_codes SET used_at = ? WHERE id = ? AND used_at IS NULL"
      ).bind(now, resetId).run();
      console.error("VoidCraft password reset email delivery failed", { status: response.status });
      return json({ error: "The reset email could not be sent. Please try again later.", code: "RESET_EMAIL_DELIVERY_FAILED" }, 503);
    }

    return json({ message: "If that email belongs to a VoidCraft account, a reset code will be sent shortly." }, 200);
  } catch (error) {
    console.error("VoidCraft password reset request failed", { name: error?.name || "Error" });
    return json({ error: "The reset request could not be completed. Please try again later.", code: "RESET_REQUEST_FAILED" }, 503);
  }
}
