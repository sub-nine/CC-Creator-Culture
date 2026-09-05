package com.sub9.userservice.auth.presentation.response;

import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import java.util.UUID;

public record CustomerSignupResponse(UUID userId, String email, String nickname, UserRole role) {

    public static CustomerSignupResponse from(User user) {
        return new CustomerSignupResponse(
                user.getId(), user.getEmail(), user.getNickname(), user.getRole());
    }
}
