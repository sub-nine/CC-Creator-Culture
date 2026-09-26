package com.sub9.productservice.wishlist.infrastructure.persistence.query;

import com.sub9.productservice.wishlist.application.port.out.WishlistQueryRepository;
import com.sub9.productservice.wishlist.domain.model.Wishlist;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WishlistQueryRepositoryImpl implements WishlistQueryRepository {
  private final WishlistQueryJpaRepository jpaRepository;

  @Override
  public Slice<Wishlist> findAllByUserId(UUID userId, Pageable pageable) {
    return jpaRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
  }
}
