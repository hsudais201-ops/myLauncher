package com.mf.mylauncher.core;

import java.util.regex.Pattern;

/** Validation for a local-only profile name; it does not authenticate a Microsoft account. */
public final class OfflineProfileValidator {
    private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("[A-Za-z0-9_]+");
    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 16;

    private OfflineProfileValidator() {
    }

    public static boolean isValid(String username) {
        return username != null
                && username.length() >= MIN_LENGTH
                && username.length() <= MAX_LENGTH
                && ALLOWED_CHARACTERS.matcher(username).matches();
    }

    public static String validationMessage(String username) {
        if (username == null || username.isEmpty()) return "Enter a username.";
        if (username.length() < MIN_LENGTH) return "Username must contain at least 3 characters.";
        if (username.length() > MAX_LENGTH) return "Username must contain no more than 16 characters.";
        if (!ALLOWED_CHARACTERS.matcher(username).matches()) {
            return "Use only English letters, numbers, and underscores.";
        }
        return "";
    }
}
