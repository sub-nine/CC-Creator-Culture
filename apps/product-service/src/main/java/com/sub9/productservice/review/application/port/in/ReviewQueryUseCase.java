package com.sub9.productservice.review.application.port.in;

import com.sub9.productservice.review.application.query.dto.ReviewInfo;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ReviewQueryUseCase {
  Slice<ReviewInfo> getReviews(UUID productId, Pageable pageable);

  Slice<ReviewInfo> getMyReviews(UUID userId, Pageable pageable);
}
