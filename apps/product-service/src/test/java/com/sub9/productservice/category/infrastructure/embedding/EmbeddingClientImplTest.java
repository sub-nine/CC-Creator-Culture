package com.sub9.productservice.category.infrastructure.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EmbeddingClientImpl 단위 테스트")
class EmbeddingClientImplTest {

    // TODO: 실제 임베딩 모델 연동 후 정상 케이스 테스트로 교체
    @Test
    @DisplayName("아직 미구현 상태라 호출하면 예외를 던진다")
    void embed_notImplementedYet_throwsException() {
        EmbeddingClientImpl embeddingClient = new EmbeddingClientImpl();

        assertThatThrownBy(() -> embeddingClient.embed("아무거나"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
