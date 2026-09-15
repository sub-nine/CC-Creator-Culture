package com.sub9.userservice.follow.presentation.response;

import com.sub9.userservice.follow.domain.model.Follow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public record FollowPageResponse(
        List<Item> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static FollowPageResponse from(Page<Follow> follows) {
        return new FollowPageResponse(
                follows.getContent().stream()
                        .map(Item::from)
                        .toList(),
                follows.getNumber(),
                follows.getSize(),
                follows.getTotalElements(),
                follows.getTotalPages(),
                follows.isFirst(),
                follows.isLast());
    }

    public record Item(
            UUID creatorId,
            String creatorName,
            Instant followedAt
    ) {

        private static Item from(Follow follow) {
            return new Item(
                    follow.getCreatorId(),
                    follow.getCreatorName(),
                    follow.getCreatedAt());
        }
    }
}
