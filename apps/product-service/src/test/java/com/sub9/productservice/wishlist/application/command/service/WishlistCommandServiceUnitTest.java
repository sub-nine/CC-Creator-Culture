package com.sub9.productservice.wishlist.application.command.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.wishlist.application.command.dto.AddToWishlistCommand;
import com.sub9.productservice.wishlist.application.command.dto.RemoveFromWishlistCommand;
import com.sub9.productservice.wishlist.application.port.out.WishlistProductPort;
import com.sub9.productservice.wishlist.domain.exception.WishlistErrorCode;
import com.sub9.productservice.wishlist.domain.repository.WishlistRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WishlistCommandService - 단위 테스트")
class WishlistCommandServiceUnitTest {
  @Mock private WishlistRepository wishlistRepository;
  @Mock private WishlistProductPort wishlistProductPort;
  @InjectMocks private WishlistCommandService wishlistCommandService;

  private final UUID userId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();

  @Test
  @DisplayName("등록할 수 없는 상품이면 PRODUCT_NOT_AVAILABLE 예외가 발생해야 한다.")
  void addToWishlist_fails_when_product_is_not_available() {
    // given
    AddToWishlistCommand command = new AddToWishlistCommand(userId, productId);
    given(wishlistProductPort.existsByProductId(productId)).willReturn(false);

    // when & then
    assertThatThrownBy(() -> wishlistCommandService.addToWishlist(command))
        .isInstanceOf(BusinessException.class)
        .hasMessage(WishlistErrorCode.PRODUCT_NOT_AVAILABLE.message());
    verifyNoInteractions(wishlistRepository);
  }

  @Test
  @DisplayName("삭제할 관심상품이 없으면 WISHLIST_NOT_FOUND 예외가 발생해야 한다.")
  void removeFromWishlist_fails_when_wishlist_is_not_found() {
    // given
    Set<UUID> wishlistIds = Set.of(UUID.randomUUID(), UUID.randomUUID());
    RemoveFromWishlistCommand command = new RemoveFromWishlistCommand(userId, wishlistIds);
    given(wishlistRepository.deleteAllByUserIdAndWishlistIds(userId, wishlistIds)).willReturn(false);

    // when & then
    assertThatThrownBy(() -> wishlistCommandService.removeFromWishlist(command))
        .isInstanceOf(BusinessException.class)
        .hasMessage(WishlistErrorCode.WISHLIST_NOT_FOUND.message());
    verifyNoInteractions(wishlistProductPort);
  }
}
