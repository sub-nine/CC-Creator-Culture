package com.sub9.userservice.auth.presentation.request;

import java.util.Locale;

final class SignupRequestNormalizer {

    private SignupRequestNormalizer() {
    }

    static String email(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    static String number(String value) {
        return value == null ? null : value.trim().replace("-", "");
    }

    static String text(String value) {
        return value == null ? null : value.trim();
    }

    static String nullableText(String value) {
        String normalized = text(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }
}
