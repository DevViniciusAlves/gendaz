package com.minhaempresa.gendaz.shared.logging;

import java.util.regex.Pattern;

/**
 * Last-resort protection for values that must never leave the application in logs.
 * Business code must still avoid logging request/response objects and personal data.
 */
public final class SensitiveDataSanitizer {
    private static final String MASK = "***";

    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![\\w.+-])[\\w.!#$%&'*+/?^`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(\\bAuthorization\\b\\s*[=:]\\s*)(?:Bearer\\s+)?[a-z0-9._~+/=-]+");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[a-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("(?i)\\beyJ[a-z0-9_-]+\\.[a-z0-9_-]+\\.[a-z0-9_-]+\\b");
    private static final Pattern JSON_SECRET = Pattern.compile(
            "(?i)(\\\"(?:senha|password|token|secret|authorization|cookie|set-cookie|api[-_]?key|client[-_]?secret|otp|codigo|telefone|phone)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")");
    private static final Pattern ASSIGNED_SECRET = Pattern.compile(
            "(?i)(\\b(?:senha|password|token|secret|cookie|set-cookie|api[-_]?key|client[-_]?secret|otp|codigo|telefone|phone)\\b\\s*[=:]\\s*)([^,;&\\s}]+)");
    private static final Pattern URI_SECRET = Pattern.compile(
            "(?i)([?&](?:senha|password|token|secret|authorization|api[-_]?key|otp|codigo)=)[^&#\\s]*");

    private SensitiveDataSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }

        String sanitized = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        sanitized = EMAIL.matcher(sanitized).replaceAll(MASK);
        sanitized = AUTHORIZATION.matcher(sanitized).replaceAll("$1" + MASK);
        sanitized = BEARER.matcher(sanitized).replaceAll("Bearer " + MASK);
        sanitized = JWT.matcher(sanitized).replaceAll(MASK);
        sanitized = JSON_SECRET.matcher(sanitized).replaceAll("$1" + MASK + "$2");
        sanitized = URI_SECRET.matcher(sanitized).replaceAll("$1" + MASK);
        sanitized = ASSIGNED_SECRET.matcher(sanitized).replaceAll("$1" + MASK);
        return sanitized;
    }
}
