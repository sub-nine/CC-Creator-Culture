package com.sub9.userservice.notification.domain.service;

import java.util.regex.Pattern;

public class SensitiveDataMasker {

    private static final Pattern PASSWORD = Pattern.compile(
            "(?i)((?:password|passwd|pwd|비밀번호)\\s*[:=]\\s*)([^\\s,}]+)"
    );
    private static final Pattern ADDRESS = Pattern.compile(
            "(?i)((?:addressDetail|detailAddress|address|상세주소|주소)\\s*[:=]\\s*)([^\\n,}]+)"
    );
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)([A-Z0-9._%+-])[A-Z0-9._%+-]*(@[A-Z0-9.-]+\\.[A-Z]{2,})"
    );
    private static final Pattern PHONE = Pattern.compile(
            "\\b(01[016789])[- ]?\\d{3,4}[- ]?(\\d{4})\\b"
    );
    private static final Pattern CARD = Pattern.compile(
            "\\b(\\d{4})[- ]?\\d{4}[- ]?\\d{4}[- ]?(\\d{4})\\b"
    );

    public String mask(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }

        String passwordMasked = PASSWORD.matcher(message).replaceAll("$1******");
        String addressMasked = ADDRESS.matcher(passwordMasked).replaceAll("$1[MASKED]");
        String emailMasked = EMAIL.matcher(addressMasked).replaceAll("$1***$2");
        String phoneMasked = PHONE.matcher(emailMasked).replaceAll("$1-****-$2");
        String cardMasked = CARD.matcher(phoneMasked).replaceAll("$1-****-****-$2");

        return cardMasked;
    }
}
