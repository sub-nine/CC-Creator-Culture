package com.sub9.productservice.review.application.command.dto;

import java.util.UUID;

public record UpdateReviewCommand(UUID userId, UUID reviewId, int rating, String content) {}
