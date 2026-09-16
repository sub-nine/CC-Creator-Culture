package com.sub9.productservice.category.application.command.port.out;

import java.util.UUID;

public interface HashtagVectorRepository {

    boolean existsByHashtagId(UUID hashtagId);

    void saveIfAbsent(UUID hashtagId, float[] vector);
}
