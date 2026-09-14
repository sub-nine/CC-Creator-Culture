package com.sub9.productservice.review.application.query;

import com.sub9.productservice.review.application.port.in.ReviewQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService implements ReviewQueryUseCase {


}
