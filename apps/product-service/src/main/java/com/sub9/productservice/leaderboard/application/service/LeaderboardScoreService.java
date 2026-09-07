package com.sub9.productservice.leaderboard.application.service;

import com.sub9.productservice.leaderboard.application.port.in.RecordOrderScoreUseCase;
import com.sub9.productservice.leaderboard.application.port.in.RecordProductViewScoreUseCase;
import com.sub9.productservice.leaderboard.application.port.out.RedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaderboardScoreService implements RecordOrderScoreUseCase, RecordProductViewScoreUseCase {
    private final RedisRepository redisRepository;

    @Override
    public void recordOrderScore(UUID orderId, UUID productId) {

    }

    @Override
    public void recordProductViewScore(LocalDate localDate, Map<UUID, Long> productViewCounts) {

    }
}
