package com.dutchlearn.logging;

/**
 * Utility helpers to avoid logging sensitive values.
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "unknown";
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 1 || atIndex == email.length() - 1) {
            return "***";
        }

        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);
        String localMasked = local.charAt(0) + "***";
        return localMasked + "@" + domain;
    }

    public static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }
}
