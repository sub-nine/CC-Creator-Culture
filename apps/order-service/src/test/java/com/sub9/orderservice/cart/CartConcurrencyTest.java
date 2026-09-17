package com.sub9.orderservice.cart;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.ErrorCode;
import com.sub9.orderservice.cart.application.dto.AddCartItemCommand;
import com.sub9.orderservice.cart.application.port.in.CartCommandUseCase;
import com.sub9.orderservice.cart.application.port.out.CartProductPort;
import com.sub9.orderservice.cart.domain.exception.CartErrorCode;
import com.sub9.orderservice.cart.domain.model.Cart;
import com.sub9.orderservice.cart.infrastructure.persistence.CartJpaRepository;
import com.sub9.orderservice.support.AbstractIntegrationTest;
import com.sub9.orderservice.support.ConcurrencyTestingUtil;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("Cart - 동시성 테스트")
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
public class CartConcurrencyTest extends AbstractIntegrationTest {
  @Autowired CartCommandUseCase cartCommandUseCase;
  @MockitoBean CartProductPort cartProductPort;
  @Autowired CartJpaRepository cartRepository;

  @AfterEach
  void tearDown() {
    cartRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("동일한 상품을 장바구니에 동시에 추가해도 하나의 항목만 저장된다.")
  void addCartItemConcurrently() throws Exception {
    // given
    Queue<ErrorCode> errorCodes = new ConcurrentLinkedQueue<>();
    UUID productId = UUID.randomUUID();
    UUID skuId = UUID.randomUUID();
    int threadCount = 2;

    AddCartItemCommand command = new AddCartItemCommand(UUID.randomUUID(), skuId, 10);
    given(cartProductPort.getValidatedProductIdForCart(skuId)).willReturn(productId);

    // when
    ConcurrencyTestingUtil.run(
        threadCount,
        () -> {
          try {
            cartCommandUseCase.addCartItem(command);
          } catch (BusinessException e) {
            errorCodes.add(e.getErrorCode());
          }
        });

    // then
    assertThat(cartRepository.findAll()).hasSize(1);
    assertThat(errorCodes).containsOnly(CartErrorCode.CART_ITEM_ALREADY_EXISTS);
  }

  @Test
  // 장바구니 수량은 정합성이 크게 필요한 영역이 아니므로
  // 최대 개수에 대한 별도의 동시성 제어는 적용하지 않는다.
  @DisplayName("장바구니 상품을 동시에 추가하면 최대 개수를 초과할 수 있다.")
  void addCartItemExceedingLimitConcurrently() throws Exception {
    // given
    UUID productId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    int threadCount = 5;

    List<Cart> carts =
        IntStream.range(0, 69)
            .mapToObj(i -> Cart.create(UUID.randomUUID(), userId, productId, UUID.randomUUID(), 10))
            .toList();

    cartRepository.saveAll(carts);
    cartRepository.flush();

    given(cartProductPort.getValidatedProductIdForCart(any())).willReturn(productId);

    // when
    ConcurrencyTestingUtil.run(
        threadCount,
        () ->
            cartCommandUseCase.addCartItem(new AddCartItemCommand(userId, UUID.randomUUID(), 10)));

    // then
    assertThat(cartRepository.count()).isGreaterThan(70);
  }
}
