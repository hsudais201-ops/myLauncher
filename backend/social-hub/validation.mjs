export const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,24}$/;

/**
 * Return the trimmed username only when the original input fits the full
 * rule. Never silently truncate user-supplied account names.
 */
export function normalizeUsername(value) {
  if (typeof value !== "string") return null;
  const username = value.trim();
  if (!USERNAME_PATTERN.test(username)) return null;
  return username;
}
