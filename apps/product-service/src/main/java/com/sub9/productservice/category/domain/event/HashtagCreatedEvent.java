package com.sub9.productservice.category.domain.event;

import java.util.UUID;

// 해시태그가 시스템에 처음 생성됐을 때만 발행됨(이미 존재하는 해시태그가 다른 상품에 추가로 링크되는 경우는 해당 없음)
public record HashtagCreatedEvent(UUID hashtagId) {
}
