package com.sub9.productservice.review.application.query;

import com.sub9.productservice.review.application.port.in.ReviewQueryUseCase;
import com.sub9.productservice.review.application.port.out.ReviewQueryRepository;
import com.sub9.productservice.review.application.query.dto.ReviewInfo;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService implements ReviewQueryUseCase {
  private final ReviewQueryRepository reviewQueryRepository;

  @Override
  public Slice<ReviewInfo> getReviews(UUID productId, Pageable pageable) {
    return reviewQueryRepository.findAllByProductId(productId, pageable);
  }

  @Override
  public Slice<ReviewInfo> getMyReviews(UUID userId, Pageable pageable) {
    return reviewQueryRepository.findAllByUserId(userId, pageable);
  }
}
