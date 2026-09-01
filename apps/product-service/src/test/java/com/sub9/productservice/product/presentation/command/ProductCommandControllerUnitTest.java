package com.sub9.productservice.product.presentation.command;

import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.productservice.config.SecurityConfig;
import com.sub9.productservice.product.application.command.dto.CreateProductCommand;
import com.sub9.productservice.product.application.command.service.ProductCommandService;
import com.sub9.productservice.product.support.AbstractControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductCommandController.class)
@DisplayName("ProductCommandController - 단위 테스트")
class ProductCommandControllerUnitTest extends AbstractControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String CREATE_ENDPOINT = "/api/v1/products";

  @Autowired MockMvc mockMvc;

  @MockitoBean ProductCommandService productCommandService;

  @Nested
  @DisplayName("상품 등록 테스트")
  class CreateProduct {
    @Test
    @DisplayName("유효한 요청이면 창작자의 ID로 상품을 등록하고 201을 반환한다")
    void when_request_is_valid_create_product_returns_created() throws Exception {
      mockMvc
          .perform(
              post(CREATE_ENDPOINT)
                  .header("X-User-Id", USER_ID)
                  .header("X-Role", "CREATOR")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(validRequest())))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.message").value("요청 성공"))
          .andExpect(jsonPath("$.data").doesNotExist());

      ArgumentCaptor<CreateProductCommand> commandCaptor =
          ArgumentCaptor.forClass(CreateProductCommand.class);

      verify(productCommandService).createProduct(commandCaptor.capture());

      CreateProductCommand command = commandCaptor.getValue();
      assertThat(command.creatorId()).isEqualTo(USER_ID);
      assertThat(command.name()).isEqualTo("왁뿌볼");
      assertThat(command.content()).isEqualTo("상품 설명");
      assertThat(command.hashTags()).containsExactly("왁뿌볼", "말랑이");
      assertThat(command.skus()).hasSize(1);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidRequests")
    @DisplayName("유효하지 않은 요청이면 400을 반환한다")
    void when_request_is_invalid_create_product_returns_validation_error(
        String context, Map<String, Object> request, String invalidField, String errorMessage)
        throws Exception {
      mockMvc
          .perform(
              post(CREATE_ENDPOINT)
                  .header("X-User-Id", USER_ID)
                  .header("X-Role", "CREATOR")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("COMMON_0003"))
          .andExpect(jsonPath("$.errors[0]['" + invalidField + "']").value(errorMessage));

      verify(productCommandService, never()).createProduct(any());
    }

    static Stream<Arguments> invalidRequests() {
      return Stream.of(
          Arguments.of("상품명이 공백인 경우", changeRequestValue("name", " "), "name", "상품명은 필수입니다."),
          Arguments.of(
              "상품 설명이 공백인 경우", changeRequestValue("content", " "), "content", "상품 설명은 필수입니다."),
          Arguments.of(
              "상품 옵션이 비어 있는 경우",
              changeRequestValue("skus", List.of()),
              "skus",
              "상품 옵션은 최소 1개 이상 등록해야합니다."),
          Arguments.of(
              "해시태그가 5개를 초과한 경우",
              changeRequestValue("hashTags", List.of("태그1", "태그2", "태그3", "태그4", "태그5", "태그6")),
              "hashTags",
              "해시태그는 최소 2개 이상 최대 5개 이하로 등록해야 합니다."),
          Arguments.of(
              "해시태그가 Null인 경우", changeRequestValue("hashTags", null), "hashTags", "해시태그는 필수입니다."),
          Arguments.of(
              "해시태그에 특수문자가 있는 경우",
              changeRequestValue("hashTags", List.of("?", "태그2")),
              "hashTags[0]",
              "해시태그는 문자와 숫자만 사용할 수 있습니다."),
          Arguments.of(
              "해시태그가 10자를 초과한 경우",
              changeRequestValue("hashTags", List.of("12345678901", "123456")),
              "hashTags[0]",
              "해시태그는 10자를 초과할 수 없습니다."));
    }

    private static Map<String, Object> validRequest() {
      return Map.of(
          "hashTags",
          List.of("왁뿌볼", "말랑이"),
          "name",
          "왁뿌볼",
          "content",
          "상품 설명",
          "skus",
          List.of(Map.of("name", "옵션", "price", 10000, "isDefault", true, "quantity", 10)));
    }

    private static Map<String, Object> changeRequestValue(String field, Object value) {
      Map<String, Object> request = new HashMap<>(validRequest());
      request.put(field, value);
      return request;
    }
  }
}
