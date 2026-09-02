package com.sub9.userservice.auth.application.service;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SignupInputNormalizer {

    public String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizePhone(String phone) {
        return removeHyphens(phone);
    }

    public String normalizeBusinessRegistrationNumber(String businessRegistrationNumber) {
        return removeHyphens(businessRegistrationNumber);
    }

    public String trim(String value) {
        return value.trim();
    }

    public String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String removeHyphens(String value) {
        return value.trim().replace("-", "");
    }
}
