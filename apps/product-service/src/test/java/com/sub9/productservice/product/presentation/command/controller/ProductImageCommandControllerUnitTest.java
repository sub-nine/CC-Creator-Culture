package com.sub9.productservice.product.presentation.command.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.application.command.dto.product.DeleteProductImageCommand;
import com.sub9.productservice.product.application.command.dto.product.UpdateImageSortOrderCommand;
import com.sub9.productservice.product.application.port.in.image.ProductImageCommandUseCase;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.presentation.command.dto.product.UpdateImageSortOrderRequest;
import com.sub9.productservice.support.AbstractControllerTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(ProductImageCommandController.class)
@DisplayName("ProductImageCommandController - 단위 테스트")
class ProductImageCommandControllerUnitTest extends AbstractControllerTest {
  @MockitoBean ProductImageCommandUseCase imageCommandUseCase;
  private final UUID creatorId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();
  private final UUID imageId = UUID.randomUUID();
  private final String endPoint = "/api/v1/products/{productId}/images";

  @Nested
  @DisplayName("이미지 순서 변경 테스트")
  class UpdateSortOrderTests {
    @Test
    @DisplayName("창작자가 이미지 순서를 변경하면 200과 성공 메시지를 반환한다.")
    void updateImage_success() throws Exception {
      // given
      var imageIds = List.of(imageId, UUID.randomUUID());
      String content = jsonMapper.writeValueAsString(new UpdateImageSortOrderRequest(imageIds));

      // when & then
      mockMvc
          .perform(
              patch(endPoint, productId)
                  .with(authentication(CustomAuthenticationToken.of(creatorId, "CREATOR")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(content))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("이미지 순서 변경에 성공했습니다."));
      verify(imageCommandUseCase)
          .updateSortOrder(new UpdateImageSortOrderCommand(productId, creatorId, imageIds));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"imageIds\":[]}"})
    @DisplayName("이미지 ID 목록이 없으면 400을 반환하고 서비스를 호출하지 않는다.")
    void updateImage_fails_when_image_ids_are_empty(String content) throws Exception {
      // when & then
      mockMvc
          .perform(
              patch(endPoint, productId)
                  .with(authentication(CustomAuthenticationToken.of(creatorId, "CREATOR")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(content))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(imageCommandUseCase);
    }

    @Test
    @DisplayName("이미지가 5개를 초과하면 400을 반환하고 서비스를 호출하지 않는다.")
    void updateImage_fails_when_image_count_exceeds_limit() throws Exception {
      // given
      var ids =
          java.util.stream.IntStream.range(0, 6).mapToObj(index -> UUID.randomUUID()).toList();

      // when & then
      mockMvc
          .perform(
              patch(endPoint, productId)
                  .with(authentication(CustomAuthenticationToken.of(creatorId, "CREATOR")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(new UpdateImageSortOrderRequest(ids))))
          .andExpect(status().isBadRequest());
      verifyNoInteractions(imageCommandUseCase);
    }

    @Test
    @DisplayName("이미지 순서 변경 실패 시 비즈니스 예외의 상태와 에러 코드를 반환한다.")
    void updateImage_fails_when_service_rejects_request() throws Exception {
      // given
      ProductErrorCode errorCode = ProductErrorCode.INVALID_PRODUCT_IMAGE_INFO;
      willThrow(new BusinessException(errorCode)).given(imageCommandUseCase).updateSortOrder(any());

      // when & then
      mockMvc
          .perform(
              patch(endPoint, productId)
                  .with(authentication(CustomAuthenticationToken.of(creatorId, "CREATOR")))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      jsonMapper.writeValueAsString(
                          new UpdateImageSortOrderRequest(List.of(imageId)))))
          .andExpect(status().is(errorCode.status().value()))
          .andExpect(jsonPath("$.errorCode").value(errorCode.code()));
    }
  }

  @Nested
  @DisplayName("이미지 삭제 테스트")
  class DeleteTests {
    @Test
    @DisplayName("창작자가 이미지를 삭제하면 200과 성공 메시지를 반환한다.")
    void deleteImage_success() throws Exception {
      // when & then
      mockMvc
          .perform(
              delete(endPoint + "/{imageId}", productId, imageId)
                  .with(authentication(CustomAuthenticationToken.of(creatorId, "CREATOR"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("이미지 삭제에 성공했습니다."));
      verify(imageCommandUseCase)
          .delete(new DeleteProductImageCommand(creatorId, productId, imageId));
    }

    @Test
    @DisplayName("삭제할 이미지가 없으면 404와 에러 코드를 반환한다.")
    void deleteImage_fails_when_image_not_found() throws Exception {
      // given
      ProductErrorCode errorCode = ProductErrorCode.PRODUCT_IMAGE_NOT_FOUND;
      willThrow(new BusinessException(errorCode)).given(imageCommandUseCase).delete(any());

      // when & then
      mockMvc
          .perform(
              delete(endPoint + "/{imageId}", productId, imageId)
                  .with(authentication(CustomAuthenticationToken.of(creatorId, "CREATOR"))))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.errorCode").value(errorCode.code()));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"PATCH", "DELETE"})
  @DisplayName("창작자 권한이 없으면 403을 반환하고 서비스를 호출하지 않는다.")
  void imageCommand_fails_when_role_is_not_creator(String method) throws Exception {
    // given
    var request =
        method.equals("PATCH")
            ? patch(endPoint, productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(
                        new UpdateImageSortOrderRequest(List.of(imageId))))
            : delete(endPoint + "/{imageId}", productId, imageId);

    // when & then
    mockMvc
        .perform(request.with(authentication(CustomAuthenticationToken.of(creatorId, "MANAGER"))))
        .andExpect(status().isForbidden());
    verifyNoInteractions(imageCommandUseCase);
  }
}
