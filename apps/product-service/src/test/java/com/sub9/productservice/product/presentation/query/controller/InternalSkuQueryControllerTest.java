package com.sub9.productservice.product.presentation.query.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.productservice.product.application.query.service.ProductQueryService;
import com.sub9.productservice.product.domain.model.ProductStatus;
import com.sub9.productservice.product.application.query.dto.SkuInfo;
import com.sub9.productservice.support.AbstractControllerTest;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(InternalSkuQueryController.class)
@DisplayName("InternalSkuQueryController - 단위 테스트")
class InternalSkuQueryControllerTest extends AbstractControllerTest {
  @MockitoBean ProductQueryService productQueryService;

  private final String endPoint = "/internal/v1/skus";

  @Test
  @DisplayName("SKU 정보 조회에 성공하면 SKU 정보와 200을 반환한다.")
  void findAllSkuInfoByIds_success() throws Exception {
    // given
    List<UUID> skuIds = List.of(UUID.randomUUID());
    UUID productId = UUID.randomUUID();
    UUID creatorId = UUID.randomUUID();

    SkuInfo response =
        new SkuInfo(
            skuIds.getFirst(),
            productId,
            creatorId,
            "왁뿌볼",
            "Ping | M",
            ProductStatus.ACTIVE,
            10000L,
            10);

    given(productQueryService.getSkus(skuIds)).willReturn(List.of(response));

    // when & then
    mockMvc
        .perform(
            post(endPoint)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(skuIds)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("요청 성공"))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].skuId").value(skuIds.getFirst().toString()))
        .andExpect(jsonPath("$.data[0].productId").value(productId.toString()))
        .andExpect(jsonPath("$.data[0].creatorId").value(creatorId.toString()))
        .andExpect(jsonPath("$.data[0].productName").value("왁뿌볼"))
        .andExpect(jsonPath("$.data[0].skuName").value("Ping | M"))
        .andExpect(jsonPath("$.data[0].productStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.data[0].price").value(10000))
        .andExpect(jsonPath("$.data[0].quantity").value(10));

    verify(productQueryService).getSkus(skuIds);
  }

  @Test
  @DisplayName("빈 SKU ID 목록을 조회하면 빈 목록과 200을 반환한다.")
  void findAllSkuInfoByIds_success_when_sku_ids_are_empty() throws Exception {
    // given
    List<UUID> skuIds = List.of();
    given(productQueryService.getSkus(skuIds)).willReturn(List.of());

    // when & then
    mockMvc
        .perform(
            post(endPoint)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(skuIds)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("요청 성공"))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());

    verify(productQueryService).getSkus(skuIds);
  }

  @Test
  @DisplayName("SKU ID가 70개를 초과하면 400을 반환한다.")
  void findAllSkuInfoByIds_fails_when_sku_ids_exceed_limit() throws Exception {
    // given
    List<UUID> skuIds = IntStream.range(0, 71).mapToObj(ignored -> UUID.randomUUID()).toList();

    // when & then
    mockMvc
        .perform(
            post(endPoint)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(skuIds)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));

    verifyNoInteractions(productQueryService);
  }
}
