package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.application.command.repository.HashtagCommandRepository;
import com.sub9.productservice.category.domain.entity.Hashtag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HashtagCommandRepositoryImpl implements HashtagCommandRepository {

    private final HashtagCommandJpaRepository jpaRepository;

    @Override
    public Hashtag save(Hashtag hashtag) {
        return jpaRepository.save(hashtag);
    }

    @Override
    public Optional<Hashtag> findById(UUID hashtagId) {
        return jpaRepository.findByIdAndDeletedAtIsNull(hashtagId);
    }
}
