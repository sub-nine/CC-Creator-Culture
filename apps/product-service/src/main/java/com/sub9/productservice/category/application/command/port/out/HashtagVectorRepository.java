package com.sub9.productservice.category.application.command.port.out;

import java.util.UUID;

public interface HashtagVectorRepository {

    boolean existsByHashtagId(UUID hashtagId);

    // 별도 트랜잭션(REQUIRES_NEW)으로 저장 - tryLink() 트랜잭션이 이후 롤백되어도 계산해둔 벡터는 유지되어야 함
    void saveIfAbsent(UUID hashtagId, float[] vector);
}
