package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.application.command.port.out.HashtagVectorRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.HashtagVector;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagVectorJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HashtagVectorRepositoryImpl implements HashtagVectorRepository {

    private final HashtagVectorJpaRepository hashtagVectorJpaRepository;

    // 별도 트랜잭션(REQUIRES_NEW)으로 실행 - 이 조회가 실패해도 tryLink()의 메인 트랜잭션(다른 후보 처리)을
    // 오염시키지 않게 분리함 (Postgres는 트랜잭션 내 쿼리 하나가 에러나면 그 트랜잭션 전체를 abort시킴)
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean existsByHashtagId(UUID hashtagId) {
        return hashtagVectorJpaRepository.existsById(hashtagId);
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
