package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.application.command.port.out.HashtagVectorRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.HashtagVector;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagVectorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HashtagVectorRepositoryImpl implements HashtagVectorRepository {

    private final HashtagVectorJpaRepository hashtagVectorJpaRepository;

    @Override
    public boolean existsByHashtagId(UUID hashtagId) {
        return hashtagVectorJpaRepository.existsById(hashtagId);
    }

    @Override
    public void saveIfAbsent(UUID hashtagId, float[] vector) {
        // TODO: 동시 호출 대비 INSERT ... ON CONFLICT DO NOTHING 형태의 원자적 upsert로 교체
        if (hashtagVectorJpaRepository.existsById(hashtagId)) {
            return;
        }
        hashtagVectorJpaRepository.save(HashtagVector.of(hashtagId, vector));
    }
}
