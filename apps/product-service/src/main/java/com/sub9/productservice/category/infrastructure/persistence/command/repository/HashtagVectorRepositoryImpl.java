package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.application.command.port.out.HashtagVectorRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.HashtagVector;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagVectorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HashtagVectorRepositoryImpl implements HashtagVectorRepository {

    private final HashtagVectorJpaRepository hashtagVectorJpaRepository;

    @Override
    public Optional<float[]> findByHashtagId(UUID hashtagId) {
        return hashtagVectorJpaRepository.findById(hashtagId).map(HashtagVector::getEmbedding);
    }

    // tryLink() 트랜잭션이 이후 실패해서 롤백되어도, 계산해둔 벡터는 별도 트랜잭션이라 살아남음
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveIfAbsent(UUID hashtagId, float[] vector) {
        // TODO: 동시 호출 대비 INSERT ... ON CONFLICT DO NOTHING 형태의 원자적 upsert로 교체
        if (hashtagVectorJpaRepository.existsById(hashtagId)) {
            return;
        }
        hashtagVectorJpaRepository.save(HashtagVector.of(hashtagId, vector));
    }
}
