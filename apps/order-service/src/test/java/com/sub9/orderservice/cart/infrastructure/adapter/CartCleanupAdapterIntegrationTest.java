package com.sub9.orderservice.cart.infrastructure.adapter;

import com.sub9.orderservice.cart.application.port.out.CartProductPort;
import com.sub9.orderservice.cart.domain.model.Cart;
import com.sub9.orderservice.cart.infrastructure.persistence.CartJpaRepository;
import com.sub9.orderservice.order.application.port.output.*;
import com.sub9.orderservice.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@SpringBootTest
@DisplayName("CartCleanupAdapter - 통합 테스트")
class CartCleanupAdapterIntegrationTest extends AbstractIntegrationTest {
  @Autowired private CartCleanupPort cartCleanupPort;
  @Autowired private CartJpaRepository cartRepository;
  @Autowired private EntityManager entityManager;

  @MockitoBean private CartProductPort cartProductPort;
  @MockitoBean private CouponApplicationPort couponApplicationPort;
  @MockitoBean private CouponUsagePort couponUsagePort;
  @MockitoBean private StockPort stockPort;
  @MockitoBean private PaymentCancellationPort paymentCancellationPort;

  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
  }

  @Nested
  @DisplayName("주문 장바구니 정리 테스트")
  class CleanupTests {
    @Test
    @DisplayName("주문에 선택한 본인 항목만 삭제하고 타인 항목과 선택하지 않은 항목은 유지한다.")
    void cleanup_success() {
      // given
      Cart first = saveCart(userId);
      Cart second = saveCart(userId);
      Cart unselected = saveCart(userId);
      Cart other = saveCart(UUID.randomUUID());

      CartCleanupCommand command = new CartCleanupCommand(UUID.randomUUID(), userId,
          List.of(first.getId(), second.getId(), other.getId(), UUID.randomUUID()));

      entityManager.flush();
      entityManager.clear();

      // when
      cartCleanupPort.cleanup(command);

      entityManager.flush();
      entityManager.clear();

      // then
      assertThat(cartRepository.findById(first.getId())).isEmpty();
      assertThat(cartRepository.findById(second.getId())).isEmpty();
      assertThat(cartRepository.findById(unselected.getId())).isPresent();
      assertThat(cartRepository.findById(other.getId())).isPresent();
      assertThat(cartRepository.countByUserId(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 정리 요청을 반복해도 성공하고 새로 담은 같은 SKU의 항목은 유지한다.")
    void cleanup_success_when_repeated() {
      // given
      Cart selected = saveCart(userId);
      CartCleanupCommand command = new CartCleanupCommand(
          UUID.randomUUID(), userId, List.of(selected.getId()));
      cartCleanupPort.cleanup(command);

      entityManager.flush();
      entityManager.clear();

      Cart added = cartRepository.save(
          Cart.create(UUID.randomUUID(), userId, selected.getSkuId(), 2));

      entityManager.flush();
      entityManager.clear();

      // when
      cartCleanupPort.cleanup(command);
      entityManager.flush();
      entityManager.clear();

      // then
      assertThat(cartRepository.findById(selected.getId())).isEmpty();
      assertThat(cartRepository.findById(added.getId())).isPresent();
      assertThat(cartRepository.countByUserId(userId)).isEqualTo(1);
    }
  }

  private Cart saveCart(UUID ownerId) {
    return cartRepository.save(
        Cart.create(UUID.randomUUID(), ownerId, UUID.randomUUID(), 3));
  }
}
