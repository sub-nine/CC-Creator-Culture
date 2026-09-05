package com.sub9.productservice.product.presentation.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.application.command.dto.DeleteSkuCommand;
import com.sub9.productservice.product.application.command.dto.UpdateSkuCommand;
import com.sub9.productservice.product.application.command.service.SkuCommandService;
import com.sub9.productservice.product.presentation.command.controller.SkuCommandController;
import com.sub9.productservice.support.AbstractControllerTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(SkuCommandController.class)
@DisplayName("SkuCommandController - 단위 테스트")
class SkuCommandControllerTest extends AbstractControllerTest {
  @MockitoBean SkuCommandService skuCommandService;

  private final UUID productId = UUID.randomUUID();
  private final UUID skuId = UUID.randomUUID();
  private final String endPoint = "/api/v1/products";

  private static final AuthUser authUser = new AuthUser(UUID.randomUUID(), "CREATOR");

  private RequestPostProcessor authUser() {
    return authentication(
        CustomAuthenticationToken.of(
            SkuCommandControllerTest.authUser.id(), SkuCommandControllerTest.authUser.role()));
  }

  @Nested
  @DisplayName("SKU 수정 테스트")
  class UpdateSkuTests {
    @Test
    @DisplayName("유효한 요청이면 SKU를 수정하고 200을 반환한다")
    void updateSku_success() throws Exception {
      // given
      UpdateSkuCommand command =
          new UpdateSkuCommand(authUser.id(), productId, skuId, "수정된 옵션", 20000L, false);
      String request =
          """
          {
            "name": "수정된 옵션",
            "price": 20000,
            "isDefault": false
          }
          """;

      // when & then
      mockMvc
          .perform(
              patch(endPoint + "/{productId}/skus/{skuId}", productId, skuId)
                  .with(authUser())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(request))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("요청 성공"))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(skuCommandService).updateSku(command);
    }

    @Test
    @DisplayName("SKU 가격이 음수이면 400을 반환한다")
    void updateSku_fails_when_price_is_negative() throws Exception {
      // given
      String request =
          """
          {
            "name": "수정된 옵션",
            "price": -1,
            "isDefault": false
          }
          """;

      // when & then
      mockMvc
          .perform(
              patch(endPoint + "/{productId}/skus/{skuId}", productId, skuId)
                  .with(authUser())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(request))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errorCode").value("COMMON_0003"))
          .andExpect(jsonPath("$.errors[0].price").value("가격은 0원 이상이어야 합니다."));

      verify(skuCommandService, never()).updateSku(any());
    }
  }

  @Nested
  @DisplayName("SKU 삭제 테스트")
  class DeleteSkuTests {
    @Test
    @DisplayName("유효한 요청이면 SKU를 삭제하고 200를 반환한다")
    void deleteSku_success() throws Exception {
      // given
      DeleteSkuCommand command = new DeleteSkuCommand(authUser.id(), productId, skuId);

      // when & then
      mockMvc
          .perform(delete(endPoint + "/" + productId + "/skus/" + skuId).with(authUser()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("요청 성공"))
          .andExpect(jsonPath("$.data").doesNotExist());

      verify(skuCommandService).deleteSku(command);
    }
  }
}
