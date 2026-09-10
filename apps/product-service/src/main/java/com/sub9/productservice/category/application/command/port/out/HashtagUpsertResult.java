package com.sub9.productservice.category.application.command.port.out;

import com.sub9.productservice.category.domain.entity.Hashtag;

// created가 true면 이번 호출로 Hashtag가 시스템에 처음 생성된 것(이미 존재해서 조회만 된 경우 false)
public record HashtagUpsertResult(Hashtag hashtag, boolean created) {
}
