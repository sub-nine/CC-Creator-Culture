package com.sub9.orderservice.cart.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sub9.orderservice.cart.application.dto.CreatorNameInfo;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartUserFeignAdapter - 단위 테스트")
class CartUserFeignAdapterUnitTest {
  @Mock private CartUserFeignClient feignClient;
  @InjectMocks private CartUserFeignAdapter adapter;

  @Test
  @DisplayName("창작자 이름 조회에 성공하면 상호명을 반환한다.")
  void getCreatorNamesByIds_success() {
    // given
    List<UUID> creatorIds = List.of(UUID.randomUUID());
    List<CreatorNameInfo> expected = List.of(new CreatorNameInfo(creatorIds.getFirst(), "상호"));
    given(feignClient.getCreatorNamesByIds(creatorIds)).willReturn(expected);

    // when
    List<CreatorNameInfo> result = adapter.getCreatorNamesByIds(creatorIds);

    // then
    assertThat(result).containsExactlyElementsOf(expected);
    verify(feignClient).getCreatorNamesByIds(creatorIds);
  }

  @Test
  @DisplayName("유저 서비스 호출에 실패하면 빈 목록을 반환한다.")
  void getCreatorNamesByIds_fails_when_service_unavailable() {
    // given
    List<UUID> creatorIds = List.of(UUID.randomUUID());

    // when
    List<CreatorNameInfo> result =
        ReflectionTestUtils.invokeMethod(
            adapter,
            "getCreatorNamesByIdsFallback",
            creatorIds,
            new RuntimeException());

    // then
    assertThat(result).isEmpty();
  }
}
