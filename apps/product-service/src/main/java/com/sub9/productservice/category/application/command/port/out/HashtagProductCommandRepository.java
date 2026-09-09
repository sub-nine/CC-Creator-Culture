package com.sub9.productservice.category.application.command.port.out;

import java.util.UUID;

public interface HashtagProductCommandRepository {

    // 이미 연결돼 있으면 false, 실제로 새로 연결된 경우에만 true를 반환
    boolean linkIfAbsent(UUID hashtagId, UUID productId);
}
