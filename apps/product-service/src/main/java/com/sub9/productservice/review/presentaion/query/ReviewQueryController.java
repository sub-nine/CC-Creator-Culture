package com.sub9.productservice.review.presentaion.query;

import com.sub9.productservice.review.application.port.in.ReviewQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ReviewQueryController {
  private final ReviewQueryUseCase reviewQueryUseCase;

  //@GetMapping("/products/{productId}/reviews")


  // @GetMapping("/reviews/me")
}
