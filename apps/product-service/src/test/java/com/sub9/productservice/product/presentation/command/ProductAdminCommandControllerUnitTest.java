package com.sub9.productservice.product.presentation.command;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.productservice.common.security.AuthUser;
import com.sub9.productservice.common.security.CustomAuthenticationToken;
import com.sub9.productservice.product.application.command.dto.UpdateProductStatusCommand;
import com.sub9.productservice.product.application.command.service.ProductCommandService;
import com.sub9.productservice.product.presentation.command.controller.ProductAdminCommandController;
import com.sub9.productservice.support.AbstractControllerTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ProductAdminCommandController.class)
@DisplayName("ProductAdminCommandController - 단위 테스트")
class ProductAdminCommandControllerUnitTest extends AbstractControllerTest {
  @MockitoBean ProductCommandService productCommandService;

  private final UUID masterId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();
  private final AuthUser authUser = new AuthUser(masterId, "MASTER");

  private RequestPostProcessor authUser() {
    return authentication(CustomAuthenticationToken.of(authUser.id(), authUser.role()));
  }

  @Test
  @DisplayName("관리자의 유효한 요청이면 상품 상태를 수정하고 200을 반환한다")
  void updateProductStatus_success() throws Exception {
    // given
    Map<String, Object> request = Map.of("status", "SUSPENDED");
    UpdateProductStatusCommand command =
        new UpdateProductStatusCommand(masterId, productId, "MASTER", "SUSPENDED");

    // when & then
    mockMvc
        .perform(
            patch("/api/v1/admin/products/{productId}/status", productId)
                .with(authUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("요청 성공"))
        .andExpect(jsonPath("$.data").doesNotExist());

    verify(productCommandService).updateStatusProduct(command);
  }
}
