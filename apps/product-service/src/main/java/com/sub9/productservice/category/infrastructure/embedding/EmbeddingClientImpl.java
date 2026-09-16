package com.sub9.productservice.category.infrastructure.embedding;

import com.sub9.productservice.category.application.command.port.out.EmbeddingClient;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingClientImpl implements EmbeddingClient {

    @Override
    public float[] embed(String text) {
        // TODO: 실제 임베딩 모델 호출 연동
        throw new UnsupportedOperationException("아직 미구현");
    }
}
