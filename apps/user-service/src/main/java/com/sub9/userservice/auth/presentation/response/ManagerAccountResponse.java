package com.sub9.userservice.auth.presentation.response;

import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import java.util.UUID;

public record ManagerAccountResponse(
        UUID userId,
        String email,
        String nickname,
        UserRole role
) {

    public static ManagerAccountResponse from(User user) {
        return new ManagerAccountResponse(
                user.getId(), user.getEmail(), user.getNickname(), user.getRole());
    }
}
