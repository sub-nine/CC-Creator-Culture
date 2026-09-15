package com.sub9.productservice.product.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.productservice.product.application.query.dto.ProductViewCount;
import com.sub9.productservice.product.infrastructure.scheduler.ProductTotalViewCountScheduler;
import com.sub9.productservice.product.infrastructure.scheduler.ProductViewCountScheduler;
import com.sub9.productservice.support.AbstractIntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@DisplayName("ProductViewRepositoryImpl - 통합 테스트")
class ProductViewRepositoryImplIntegrationTest extends AbstractIntegrationTest {
  @Autowired private ProductViewRepositoryImpl productViewRepository;

  @MockitoBean private ProductViewCountScheduler productViewCountScheduler;
  @MockitoBean private ProductTotalViewCountScheduler productTotalViewCountScheduler;
  @MockitoBean private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

  @BeforeEach
  void setUp() {
    productViewRepository.deleteAllViewCount();
  }

  @Test
  @DisplayName("동일 사용자의 상품의 중복 조회를 막는다.")
  void recordView_success_when_duplicate_view() {
    // given
    UUID productId = UUID.randomUUID();
    UUID otherProductId = UUID.randomUUID();
    String viewerId = "user:" + UUID.randomUUID();
    Duration ttl = Duration.ofMinutes(30);

    // when & then
    assertThat(productViewRepository.recordView(productId, viewerId, ttl)).isTrue();
    assertThat(productViewRepository.recordView(productId, viewerId, ttl)).isFalse();
    assertThat(productViewRepository.recordView(productId, "guest:" + UUID.randomUUID(), ttl)).isTrue();
    assertThat(productViewRepository.recordView(otherProductId, viewerId, ttl)).isTrue();
    assertThat(productViewRepository.findAllViewCounts())
        .containsExactlyInAnyOrder(
            new ProductViewCount(productId, 2L), new ProductViewCount(otherProductId, 1L));

    productViewRepository.deleteAllViewCount();
    assertThat(productViewRepository.findAllViewCounts()).isEmpty();
    assertThat(productViewRepository.recordView(productId, viewerId, ttl)).isFalse();
    assertThat(productViewRepository.findAllViewCounts()).isEmpty();
  }
}
