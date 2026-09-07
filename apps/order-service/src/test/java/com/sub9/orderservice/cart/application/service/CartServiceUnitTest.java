package com.sub9.orderservice.cart.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.cart.application.dto.AddCartItemCommand;
import com.sub9.orderservice.cart.application.port.CartProductPort;
import com.sub9.orderservice.cart.domain.exception.CartErrorCode;
import com.sub9.orderservice.cart.domain.repository.CartRepository;
import com.sub9.orderservice.cart.infrastructure.feign.exception.CartProductClientErrorCode;
import java.util.UUID;
import java.util.Optional;
import com.sub9.orderservice.cart.application.dto.UpdateCartItemCommand;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService - 단위 테스트")
class CartServiceUnitTest {
  @Mock private CartRepository cartRepository;
  @Mock private CartProductPort cartProductPort;
  @InjectMocks private CartService cartService;

  private AddCartItemCommand command;

  @BeforeEach
  void setUp() {
    command = new AddCartItemCommand(UUID.randomUUID(), UUID.randomUUID(), 2);
  }

  @Nested
  @DisplayName("장바구니 등록 실패 테스트")
  class AddCartItemTests {
    @Test
    @DisplayName("장바구니에 70개가 등록되어 있으면 CART_ITEM_LIMIT_EXCEEDED 예외가 발생해야한다.")
    void addCartItem_fails_when_cart_item_limit_exceeded() {
      // given
      given(cartRepository.countByUserId(command.userId())).willReturn(70);

      // when & then
      assertThatThrownBy(() -> cartService.addCartItem(command))
          .isInstanceOf(BusinessException.class)
          .hasMessage(CartErrorCode.CART_ITEM_LIMIT_EXCEEDED.message());
      verifyNoInteractions(cartProductPort);
      verify(cartRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("중복된 상품이 있으면 CART_ITEM_ALREADY_EXISTS 예외가 발생해야한다.")
    void addCartItem_fails_when_cart_item_already_exists() {
      // given
      given(cartRepository.saveAndFlush(any()))
          .willThrow(new DataIntegrityViolationException("중복 저장"));

      // when & then
      assertThatThrownBy(() -> cartService.addCartItem(command))
          .isInstanceOf(BusinessException.class)
          .hasMessage(CartErrorCode.CART_ITEM_ALREADY_EXISTS.message());
      verify(cartProductPort).validateSkuForCart(command.skuId());
    }

    @Test
    @DisplayName("유효하지 않은 상품일 시 INVALID_CART_PRODUCT 예외가 발생해야한다..")
    void addCartItem_fails_when_product_invalid() {
      // given
      willThrow(new BusinessException(CartProductClientErrorCode.INVALID_CART_PRODUCT))
          .given(cartProductPort)
          .validateSkuForCart(command.skuId());

      // when & then
      assertThatThrownBy(() -> cartService.addCartItem(command))
          .isInstanceOf(BusinessException.class)
          .hasMessage(CartProductClientErrorCode.INVALID_CART_PRODUCT.message());
      verify(cartRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("상품 서비스 장애가 발생하면 SERVICE_UNAVAILABLE 예외가 발생해야한다..")
    void addCartItem_fails_when_product_service_unavailable() {
      // given
      willThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE))
          .given(cartProductPort)
          .validateSkuForCart(command.skuId());

      // when & then
      assertThatThrownBy(() -> cartService.addCartItem(command))
          .isInstanceOf(BusinessException.class)
          .hasMessage(CommonErrorCode.SERVICE_UNAVAILABLE.message());
      verify(cartRepository, never()).saveAndFlush(any());
    }
  }

  @Nested
  @DisplayName("장바구니 수정 실패 테스트")
  class UpdateCartItemTests {
    @Test
    @DisplayName("해당 상품이 등록되어 있지 않으면 CART_ITEM_NOT_FOUND 예외가 발생해야한다.")
    void updateCartItem_fails_when_cart_item_not_found() {
      // given
      UpdateCartItemCommand updateCommand =
          new UpdateCartItemCommand(command.userId(), UUID.randomUUID(), 3);
      given(cartRepository.findByIdAndUserId(updateCommand.cartId(), updateCommand.userId()))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> cartService.updateCartItem(updateCommand))
          .isInstanceOf(BusinessException.class)
          .hasMessage(CartErrorCode.CART_ITEM_NOT_FOUND.message());
      verify(cartRepository).findByIdAndUserId(updateCommand.cartId(), updateCommand.userId());
      verifyNoInteractions(cartProductPort);
    }
  }
}
