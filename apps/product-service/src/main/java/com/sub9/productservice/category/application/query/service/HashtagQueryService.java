package com.sub9.productservice.category.application.query.service;

import com.sub9.productservice.category.application.query.repository.HashtagQueryRepository;
import com.sub9.productservice.category.presentation.query.dto.HashtagResponse;
import com.sub9.productservice.leaderboard.application.port.HashtagQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HashtagQueryService implements HashtagQueryPort {
    private final HashtagQueryRepository hashtagQueryRepository;

    @Override
    public List<HashtagResponse> getHashtagByIds(List<UUID> ids) {
        return hashtagQueryRepository.searchHashtagsByIds(ids);
    }
}
