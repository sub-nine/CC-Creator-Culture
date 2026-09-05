package com.sub9.productservice.product.presentation.query.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.productservice.product.application.query.service.ProductQueryService;
import com.sub9.productservice.product.domain.model.ProductStatus;
import com.sub9.productservice.product.application.query.dto.ProductDetailInfo;
import com.sub9.productservice.product.application.query.dto.ProductInfo;
import com.sub9.productservice.support.AbstractControllerTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(ProductQueryController.class)
class ProductQueryControllerUnitTest extends AbstractControllerTest {
  @MockitoBean ProductQueryService productQueryService;

  private final UUID productId = UUID.randomUUID();
  private final String endPoint = "/api/v1/products";

  @Test
  @DisplayName("상품 검색에 성공하면 상품 목록과 200을 반환한다.")
  void searchProducts_success() throws Exception {
    // given
    String keyword = "왁뿌볼";
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
    ProductInfo response =
        new ProductInfo(
            productId, "왁뿌볼", ProductStatus.ACTIVE, BigDecimal.valueOf(4.5), 3L, 10000L, 10);

    given(productQueryService.searchProducts(eq(keyword), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(response), pageable, 1));

    // when & then
    mockMvc
        .perform(get(endPoint).param("keyword", keyword).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("요청 성공"))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].productId").value(productId.toString()))
        .andExpect(jsonPath("$.data.content[0].name").value("왁뿌볼"))
        .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.content[0].averageRating").value(4.5))
        .andExpect(jsonPath("$.data.content[0].reviewCount").value(3))
        .andExpect(jsonPath("$.data.content[0].price").value(10000))
        .andExpect(jsonPath("$.data.content[0].quantity").value(10))
        .andExpect(jsonPath("$.data.totalElements").value(1));

    verify(productQueryService).searchProducts(keyword, pageable);
  }

  @Test
  @DisplayName("상품 상세 정보 조회에 성공하면 상품 정보와 200을 반환한다.")
  void getProductDetail_success() throws Exception {
    // given
    ProductDetailInfo response =
        new ProductDetailInfo(
            productId,
            UUID.randomUUID(),
            "왁뿌볼",
            "설명",
            ProductStatus.ACTIVE,
            0L,
            BigDecimal.valueOf(4.5),
            0L,
            null,
            null,
            null);

    given(productQueryService.getProductDetail(eq(productId), any())).willReturn(response);

    String jsonProductDetail = jsonMapper.writeValueAsString(ApiResponse.success(response));

    // when & then
    mockMvc
        .perform(get(endPoint + "/{productId}", productId).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("요청 성공"))
        .andExpect(content().json(jsonProductDetail));

    verify(productQueryService).getProductDetail(productId, null);
  }
}
