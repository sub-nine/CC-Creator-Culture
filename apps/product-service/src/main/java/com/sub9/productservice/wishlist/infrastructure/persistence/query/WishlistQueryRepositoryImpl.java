package com.sub9.productservice.wishlist.infrastructure.persistence.query;

import static com.sub9.productservice.wishlist.domain.model.QWishlist.wishlist;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sub9.productservice.wishlist.application.port.out.WishlistQueryRepository;
import com.sub9.productservice.wishlist.domain.model.Wishlist;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WishlistQueryRepositoryImpl implements WishlistQueryRepository {
  private final JPAQueryFactory queryFactory;

  @Override
  public Slice<Wishlist> findAllByUserId(UUID userId, Pageable pageable) {
    int size = pageable.getPageSize();

    List<Wishlist> result =
        queryFactory
            .selectFrom(wishlist)
            .where(wishlist.userId.eq(userId))
            .orderBy(wishlist.createdAt.desc(), wishlist.id.desc())
            .offset(pageable.getOffset())
            .limit(size + 1)
            .fetch();

    boolean hasNext = result.size() > size;

    return new SliceImpl<>(hasNext ? result.subList(0, size) : result, pageable, hasNext);
  }
}
