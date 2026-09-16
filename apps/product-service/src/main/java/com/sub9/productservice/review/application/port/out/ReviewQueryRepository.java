package com.sub9.productservice.review.application.port.out;

import com.sub9.productservice.review.application.query.dto.ReviewInfo;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ReviewQueryRepository {
  Slice<ReviewInfo> findAllByProductId(UUID productId, Pageable pageable);

  Slice<ReviewInfo> findAllByUserId(UUID userId, Pageable pageable);
}
