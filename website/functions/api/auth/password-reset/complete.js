import { allowAttempt, constantTimeEqual, json, passwordHash, randomHex, readJson, sameOrigin } from "../_utils.js";

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
  if (!env?.VOIDCRAFT_AUTH) return json({ error: "The account service is not connected.", code: "AUTH_DB_NOT_BOUND" }, 503);
  if (!env.PASSWORD_RESET_SECRET) {
    return json({ error: "Password reset is not configured by the site owner yet.", code: "RESET_SECRET_NOT_CONFIGURED" }, 503);
  }

  const limit = await allowAttempt(request, env, "password-reset-complete");
  if (!limit.allowed) {
    return json({ error: "Too many reset attempts. Please try again later.", code: "RESET_RATE_LIMITED" }, 429, { "Retry-After": String(limit.retryAfter) });
  }

  try {
    const body = await readJson(request);
    const email = typeof body?.email === "string" ? body.email.trim().toLowerCase() : "";
    const code = typeof body?.code === "string" ? body.code.trim() : "";
    const newPassword = typeof body?.newPassword === "string" ? body.newPassword : "";

    if (!email || email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      return json({ error: "Enter the email address you requested the code for.", code: "RESET_INPUT_INVALID" }, 400);
    }
    if (!/^\d{6}$/.test(code)) return json({ error: "Enter the six-digit code from your email.", code: "RESET_CODE_INVALID" }, 400);
    if (newPassword.length < 12 || newPassword.length > 128) {
      return json({ error: "Your new password must be 12–128 characters long.", code: "RESET_PASSWORD_INVALID" }, 400);
    }

    const user = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT id FROM users WHERE email = ? COLLATE NOCASE LIMIT 1"
    ).bind(email).first();
    if (!user) return json({ error: "The code is invalid or expired. Request a new code and try again.", code: "RESET_CODE_INVALID" }, 400);

    const now = Math.floor(Date.now() / 1000);
    const reset = await env.VOIDCRAFT_AUTH.prepare(
      "SELECT id, code_hash, expires_at, attempts FROM password_reset_codes " +
      "WHERE user_id = ? AND used_at IS NULL ORDER BY created_at DESC LIMIT 1"
    ).bind(user.id).first();

    if (!reset || Number(reset.expires_at) <= now || Number(reset.attempts) >= 5) {
      return json({ error: "The code is invalid or expired. Request a new code and try again.", code: "RESET_CODE_INVALID" }, 400);
    }

    const calculated = await codeHash(env.PASSWORD_RESET_SECRET, reset.id, code);
    if (!constantTimeEqual(calculated, reset.code_hash)) {
      await env.VOIDCRAFT_AUTH.prepare(
        "UPDATE password_reset_codes SET attempts = attempts + 1 WHERE id = ? AND used_at IS NULL"
      ).bind(reset.id).run();
      return json({ error: "The code is invalid or expired. Check it and try again, or request a new code.", code: "RESET_CODE_INVALID" }, 400);
    }

    const salt = randomHex(16);
    const hash = await passwordHash(newPassword, salt);
    await env.VOIDCRAFT_AUTH.prepare(
      "UPDATE users SET password_salt = ?, password_hash = ?, last_login_at = NULL WHERE id = ?"
    ).bind(salt, hash, user.id).run();
    await env.VOIDCRAFT_AUTH.prepare(
      "UPDATE password_reset_codes SET used_at = ? WHERE user_id = ? AND used_at IS NULL"
    ).bind(now, user.id).run();
    // Revoke all prior sessions so a forgotten/lost password cannot leave sessions alive.
    await env.VOIDCRAFT_AUTH.prepare("DELETE FROM sessions WHERE user_id = ?").bind(user.id).run();

    return json({ message: "Your password has been changed. Sign in with your new password." }, 200);
  } catch (error) {
    console.error("VoidCraft password reset completion failed", { name: error?.name || "Error" });
    return json({ error: "The password could not be reset. Request a new code and try again.", code: "RESET_COMPLETION_FAILED" }, 503);
  }
}
