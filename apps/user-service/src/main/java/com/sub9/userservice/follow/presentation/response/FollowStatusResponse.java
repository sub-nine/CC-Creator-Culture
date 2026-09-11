package com.sub9.userservice.follow.presentation.response;

import java.util.UUID;

public record FollowStatusResponse(
        UUID creatorId,
        boolean following
) {

    public static FollowStatusResponse followed(UUID creatorId) {
        return new FollowStatusResponse(creatorId, true);
    }
}
