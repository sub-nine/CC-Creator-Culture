package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sub9.productservice.category.application.command.port.out.HashtagProductCommandRepository;
import com.sub9.productservice.category.domain.entity.HashtagProduct;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagProductJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HashtagProductCommandRepositoryImpl implements HashtagProductCommandRepository {

    private final HashtagProductJpaRepository jpaRepository;

    @Override
    public boolean linkIfAbsent(UUID hashtagId, UUID productId) {
        return jpaRepository.insertLinkIfAbsent(
                UuidCreator.getTimeOrderedEpoch(), hashtagId, productId, HashtagProduct.ACTIVE_UNIQUE_VERSION)
                .isPresent();
    }
}
