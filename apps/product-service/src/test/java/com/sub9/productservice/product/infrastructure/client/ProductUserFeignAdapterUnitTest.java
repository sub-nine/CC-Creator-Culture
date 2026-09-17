package com.sub9.productservice.product.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sub9.productservice.product.application.port.out.product.CreatorNameInfo;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductUserFeignAdapter - 단위 테스트")
class ProductUserFeignAdapterUnitTest {
  @Mock private ProductUserFeignClient feignClient;
  @InjectMocks private ProductUserFeignAdapter adapter;

  @Test
  @DisplayName("창작자 조회에 성공하면 ID별 상호명 Map을 반환한다.")
  void getCreatorNamesByIds_success() {
    UUID creatorId = UUID.randomUUID();
    List<UUID> creatorIds = List.of(creatorId);
    given(feignClient.getCreatorNamesByIds(creatorIds))
        .willReturn(List.of(new CreatorNameInfo(creatorId, "상호")));

    Map<UUID, String> result = adapter.getCreatorNamesByIds(creatorIds);

    assertThat(result).containsEntry(creatorId, "상호");
    verify(feignClient).getCreatorNamesByIds(creatorIds);
  }

  @Test
  @DisplayName("User-Service 서버 연결에 실패하면 빈 Map을 반환한다.")
  void getCreatorNamesByIds_fails_when_service_unavailable() {
    List<UUID> creatorIds = List.of(UUID.randomUUID());

    Map<UUID, String> result =
        ReflectionTestUtils.invokeMethod(
            adapter,
            "getCreatorNamesByIdsFallback",
            creatorIds,
            new RuntimeException());

    assertThat(result).isEmpty();
  }
}
