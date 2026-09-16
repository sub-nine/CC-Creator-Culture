package com.sub9.productservice.category.infrastructure.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingClientImpl 단위 테스트")
class EmbeddingClientImplTest {

    @Mock
    private EmbeddingFeignClient embeddingFeignClient;

    private EmbeddingClientImpl embeddingClient;

    @BeforeEach
    void setUp() {
        embeddingClient = new EmbeddingClientImpl(embeddingFeignClient);
    }

    @Test
    @DisplayName("텍스트를 Feign 클라이언트에 넘기고 응답 벡터를 그대로 반환한다")
    void embed_delegatesToFeignClient_returnsVector() {
        float[] vector = {0.1f, 0.2f, 0.3f};
        when(embeddingFeignClient.embed(new EmbeddingRequest("말랑이"))).thenReturn(new EmbeddingResponse(vector));

        float[] result = embeddingClient.embed("말랑이");

        assertThat(result).isEqualTo(vector);
    }
}
