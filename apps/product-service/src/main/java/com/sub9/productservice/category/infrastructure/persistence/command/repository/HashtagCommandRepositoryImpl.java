package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
import com.sub9.productservice.category.application.command.port.out.HashtagUpsertResult;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HashtagCommandRepositoryImpl implements HashtagCommandRepository {

    private static final int MAX_USAGE_COUNT_RETRY = 3;

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
    public Hashtag increaseUsageCount(UUID hashtagId) {
        OptimisticLockingFailureException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_USAGE_COUNT_RETRY; attempt++) {
            Hashtag hashtag = findById(hashtagId)
                    .orElseThrow(() -> new IllegalStateException("Hashtag 없음 - id: " + hashtagId));

            hashtag.increaseUsageCount();

            try {
                return jpaRepository.saveAndFlush(hashtag);
            } catch (OptimisticLockingFailureException e) {
                lastFailure = e;
            }
        }

        throw lastFailure;
    }
}
