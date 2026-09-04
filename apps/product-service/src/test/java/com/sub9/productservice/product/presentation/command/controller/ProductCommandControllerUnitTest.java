package com.sub9.productservice.product.presentation.command.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.application.command.dto.CreateProductCommand;
import com.sub9.productservice.product.application.command.dto.UpdateProductCommand;
import com.sub9.productservice.product.application.command.dto.UpdateProductStatusCommand;
import com.sub9.productservice.product.application.command.service.ProductCommandService;
import com.sub9.productservice.support.AbstractControllerTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ProductCommandController.class)
@DisplayName("ProductCommandController - 단위 테스트")
class ProductCommandControllerUnitTest extends AbstractControllerTest {
  @MockitoBean ProductCommandService productCommandService;

  private final UUID userId = UUID.randomUUID();
  private final String endPoint = "/api/v1/products";

  private final AuthUser authUser = new AuthUser(userId, "CREATOR");

  private RequestPostProcessor authUser(AuthUser authUser) {
    return authentication(CustomAuthenticationToken.of(authUser.id(), authUser.role()));
  }

  @Nested
  @DisplayName("상품 등록 테스트")
  class CreateProduct {
    @Test
    @DisplayName("창작자의 ID로 상품을 등록하고 201을 반환한다")
    void when_request_is_valid_create_product_returns_created() throws Exception {
      mockMvc
          .perform(
              post(endPoint)
                  .with(authUser(authUser))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(validRequest())))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.message").value("요청 성공"))
          .andExpect(jsonPath("$.data").doesNotExist());

      ArgumentCaptor<CreateProductCommand> commandCaptor =
          ArgumentCaptor.forClass(CreateProductCommand.class);

      verify(productCommandService).createProduct(commandCaptor.capture());

      CreateProductCommand command = commandCaptor.getValue();
      assertThat(command.creatorId()).isEqualTo(userId);
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
              post(endPoint)
                  .with(authUser(authUser))
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
              "해시태그는 최소 1개 이상 최대 5개 이하로 등록해야 합니다."),
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

  @Nested
  @DisplayName("상품 정보 수정 테스트")
  class UpdateProduct {
    @Test
    @DisplayName("상품 정보를 수정하고 200을 반환한다")
    void updateProduct_success() throws Exception {
      // given
      UUID productId = UUID.randomUUID();
      Map<String, Object> request = Map.of("name", "수정된 상품명", "content", "수정된 상품 설명");
      UpdateProductCommand command =
          new UpdateProductCommand(authUser.id(), productId, "수정된 상품명", "수정된 상품 설명");

      // when & then
      mockMvc
          .perform(
              patch(endPoint + "/{productId}", productId)
                  .with(authUser(authUser))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("요청 성공"))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(productCommandService).updateProduct(command);
    }

    @Test
    @DisplayName("상품명이 공백이면 400을 반환한다")
    void updateProduct_fails_when_name_is_blank() throws Exception {
      // given
      UUID productId = UUID.randomUUID();
      Map<String, Object> request = Map.of("name", " ", "content", "수정된 상품 설명");

      // when & then
      mockMvc
          .perform(
              patch(endPoint + "/{productId}", productId)
                  .with(authUser(authUser))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("COMMON_0003"))
          .andExpect(jsonPath("$.errors[0].name").value("상품명은 필수입니다."));

      verify(productCommandService, never()).updateProduct(any());
    }
  }

  @Nested
  @DisplayName("상품 상태 수정 테스트")
  class UpdateStatusProduct {
    @Test
    @DisplayName("상품 상태를 수정하고 200을 반환한다")
    void updateStatusProduct_success() throws Exception {
      // given
      UUID productId = UUID.randomUUID();
      Map<String, Object> request = Map.of("status", "INACTIVE");
      UpdateProductStatusCommand command =
          new UpdateProductStatusCommand(authUser.id(), productId, authUser.role(), "INACTIVE");

      // when & then
      mockMvc
          .perform(
              patch(endPoint + "/{productId}/status", productId)
                  .with(authUser(authUser))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("요청 성공"))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(productCommandService).updateStatusProduct(command);
    }

    @Test
    @DisplayName("상품 상태가 null이면 400을 반환한다")
    void updateStatusProduct_fails_when_status_is_null() throws Exception {
      // given
      UUID productId = UUID.randomUUID();
      String request = "{\"status\":null}";

      // when & then
      mockMvc
          .perform(
              patch(endPoint + "/{productId}/status", productId)
                  .with(authUser(authUser))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(request))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("COMMON_0003"))
          .andExpect(jsonPath("$.errors[0].status").value("상품 상태는 필수 입력 값입니다."));

      verify(productCommandService, never()).updateStatusProduct(any());
    }
  }

  @Nested
  @DisplayName("상품 삭제 테스트")
  class DeleteProduct {
    @Test
    @DisplayName("상품을 삭제하고 200를 반환한다")
    void deleteProduct_success() throws Exception {
      // given
      UUID productId = UUID.randomUUID();

      // when & then
      mockMvc
          .perform(delete(endPoint + "/{productId}", productId).with(authUser(authUser)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("요청 성공"))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(productCommandService).deleteProduct(authUser.id(), productId);
    }
  }
}
