package com.sub9.productservice.category.infrastructure.embedding;

import com.sub9.productservice.category.application.command.port.out.EmbeddingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmbeddingClientImpl implements EmbeddingClient {

    private final EmbeddingFeignClient embeddingFeignClient;

    @Override
    public float[] embed(String text) {
        return embeddingFeignClient.embed(new EmbeddingRequest(text)).vector();
    }
}
