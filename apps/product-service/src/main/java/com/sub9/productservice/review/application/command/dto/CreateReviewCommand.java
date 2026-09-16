package com.sub9.productservice.review.application.command.dto;

import java.util.UUID;

public record CreateReviewCommand(
    UUID userId, UUID orderItemId, int rating, String content) {}
