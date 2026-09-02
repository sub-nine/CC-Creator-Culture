package com.sub9.userservice.auth.presentation.response;

import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.user.domain.model.User;
import com.sub9.userservice.user.domain.model.UserRole;
import java.util.UUID;

public record CreatorSignupResponse(
        UUID userId,
        String email,
        String nickname,
        UserRole role,
        UUID creatorId,
        ApprovalStatus approvalStatus
) {

    public static CreatorSignupResponse from(User user, Creator creator) {
        return new CreatorSignupResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                creator.getId(),
                creator.getApprovalStatus());
    }
}
