package com.sub9.userservice.creator.presentation.response;

import java.util.UUID;

public record FollowerCountResponse(UUID creatorId, long followerCount) {
}
