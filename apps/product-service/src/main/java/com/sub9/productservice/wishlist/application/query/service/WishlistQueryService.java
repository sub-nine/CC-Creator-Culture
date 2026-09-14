package com.sub9.productservice.wishlist.application.query.service;

import com.sub9.productservice.wishlist.application.port.in.WishlistQueryUseCase;
import com.sub9.productservice.wishlist.application.port.out.WishlistProductPort;
import com.sub9.productservice.wishlist.application.port.out.WishlistQueryRepository;
import com.sub9.productservice.wishlist.application.query.dto.WishlistInfo;
import com.sub9.productservice.wishlist.domain.model.Wishlist;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistQueryService implements WishlistQueryUseCase {
  private final WishlistQueryRepository wishlistQueryRepository;
  private final WishlistProductPort wishlistProductPort;

  @Override
  public Slice<WishlistInfo> getWishlist(UUID userId, Pageable pageable) {
    Slice<Wishlist> wishlists = wishlistQueryRepository.findAllByUserId(userId, pageable);

    Set<UUID> productIds =
        wishlists.getContent().stream().map(Wishlist::getProductId).collect(Collectors.toSet());

    Map<UUID, WishlistInfo> productMap = wishlistProductPort.findAllByProductIds(productIds);

    // 일반적인 상황은 아니지만 Kafka 메시지 수신 후 상품 정보가 바뀌기 전 까지 상품 개수가 적게 보일 수 있음.
    List<WishlistInfo> content =
        wishlists.getContent().stream()
            .filter(wishlist -> productMap.containsKey(wishlist.getProductId()))
            .map(wishlist -> WishlistInfo.of(wishlist, productMap.get(wishlist.getProductId())))
            .toList();

    return new SliceImpl<>(content, pageable, wishlists.hasNext());
  }
}
