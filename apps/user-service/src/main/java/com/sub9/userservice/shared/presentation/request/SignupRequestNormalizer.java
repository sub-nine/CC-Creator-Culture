package com.sub9.userservice.shared.presentation.request;

import java.util.Locale;

public final class SignupRequestNormalizer {

    private SignupRequestNormalizer() {
    }

    public static String email(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public static String number(String value) {
        return value == null ? null : value.trim().replace("-", "");
    }

    public static String text(String value) {
        return value == null ? null : value.trim();
    }

    public static String nullableText(String value) {
        String normalized = text(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }
}
