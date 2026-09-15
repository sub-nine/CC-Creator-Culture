package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
import com.sub9.productservice.category.application.command.model.HashtagUpsertResult;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HashtagCommandRepositoryImpl implements HashtagCommandRepository {

    private final HashtagJpaRepository jpaRepository;

    @Override
    public Hashtag save(Hashtag hashtag) {
        return jpaRepository.save(hashtag);
    }

    @Override
    public Optional<Hashtag> findById(UUID hashtagId) {
        return jpaRepository.findByIdAndDeletedAtIsNull(hashtagId);
    }

    @Override
    public HashtagUpsertResult findOrCreateByName(String name) {
        boolean created = jpaRepository
                .insertIfAbsent(UuidCreator.getTimeOrderedEpoch(), name, Hashtag.ACTIVE_UNIQUE_VERSION)
                .isPresent();

        Hashtag hashtag = jpaRepository.findByNameAndDeletedAtIsNull(name)
                .orElseThrow(() -> new IllegalStateException("Hashtag upsert 직후 조회 실패 - name: " + name));

        return new HashtagUpsertResult(hashtag, created);
    }

    @Override
    @Transactional
    public void increaseUsageCount(UUID hashtagId) {
        int updated = jpaRepository.increaseUsageCount(hashtagId);
        if (updated == 0) {
            throw new IllegalStateException("Hashtag 없음 - id: " + hashtagId);
        }
    }
}
