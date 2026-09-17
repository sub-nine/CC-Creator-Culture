package com.sub9.productservice.category.application.command.port.in;

public interface RetryUnlinkedHashtagsUseCase {
    // 이번 배치에서 재연결을 시도한 해시태그 수를 반환
    int retryUnlinkedHashtags();
}
