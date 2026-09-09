package com.sub9.userservice.notification.domain.service;

import java.util.regex.Pattern;

public class SensitiveDataMasker {


    private static final Pattern ADDRESS =
            Pattern.compile(
            "(?i)((?:addressDetail|detailAddress|address|상세주소|주소)\\s*[:=]\\s*)([^\\n,}]+)"
    );
    private static final Pattern EMAIL =
            Pattern.compile(
            "(?i)([A-Z0-9._%+-])[A-Z0-9._%+-]*(@[A-Z0-9.-]+\\.[A-Z]{2,})"
    );

    public String mask(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }

        String addressMasked =
                ADDRESS.matcher(message).replaceAll("$1[MASKED]");
        return
                EMAIL.matcher(addressMasked).replaceAll("$1***$2");

    }
}
