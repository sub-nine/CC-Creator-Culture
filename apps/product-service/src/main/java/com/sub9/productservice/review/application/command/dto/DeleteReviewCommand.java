package com.sub9.productservice.review.application.command.dto;

import java.util.UUID;

public record DeleteReviewCommand(UUID userId, UUID reviewId) {}
