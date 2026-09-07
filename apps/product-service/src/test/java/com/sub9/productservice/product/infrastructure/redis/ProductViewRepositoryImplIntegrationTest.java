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

  // 테스트 중 자동 집계와 외부 Kafka 소비가 실행되지 않도록 격리한다.
  @MockitoBean private ProductViewCountScheduler productViewCountScheduler;
  @MockitoBean private ProductTotalViewCountScheduler productTotalViewCountScheduler;
  @MockitoBean private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

  @BeforeEach
  void setUp() {
    productViewRepository.deleteAllViewCount();
  }

  @Test
  @DisplayName("동일 사용자와 상품의 중복 조회를 막고 다른 사용자와 상품은 집계한다.")
  void recordView_success_when_duplicate_view() {
    // given
    UUID productId = UUID.randomUUID();
    UUID otherProductId = UUID.randomUUID();
    UUID viewerId = UUID.randomUUID();
    Duration ttl = Duration.ofMinutes(30);

    // when & then
    assertThat(productViewRepository.recordView(productId, viewerId, ttl)).isTrue();
    assertThat(productViewRepository.recordView(productId, viewerId, ttl)).isFalse();
    assertThat(productViewRepository.recordView(productId, UUID.randomUUID(), ttl)).isTrue();
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
