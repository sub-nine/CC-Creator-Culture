package com.sub9.productservice.category.infrastructure.embedding;

import com.sub9.productservice.common.config.feign.OpenFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "embedding-client", url = "${embedding.client.base-url}", configuration = OpenFeignConfig.class)
public interface EmbeddingFeignClient {

    @PostMapping("/embed")
    EmbeddingResponse embed(@RequestBody EmbeddingRequest request);
}
