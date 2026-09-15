package com.sub9.userservice.user.presentation.response;

import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import java.util.UUID;

public record MyProfileResponse(
        UUID userId,
        String email,
        String nickname,
        String phone,
        String address,
        String slackId,
        UserRole role) {

    public static MyProfileResponse from(User user) {
        return new MyProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getPhone(),
                user.getAddress(),
                user.getSlackId(),
                user.getRole());
    }
}
