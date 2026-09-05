package com.sub9.userservice.notification.application.dto;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;


public record AuthenticatedUser(UUID userId, String role) {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId must not be null");
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("userRole must not be blank");
        }
        role = role.trim().toUpperCase(Locale.ROOT);
    }
}
