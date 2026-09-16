package com.sub9.productservice.review.application.query.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewInfo(
    UUID reviewId, UUID productId, UUID userId, int rating, String content, Instant createdAt) {}
