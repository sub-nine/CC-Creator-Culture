package com.sub9.productservice.leaderboard.application.port;

import com.sub9.productservice.category.presentation.query.dto.HashtagResponse;

import java.util.List;
import java.util.UUID;

public interface HashtagQueryPort {
    List<HashtagResponse> getHashtagByIds(List<UUID> targetIds);
}
