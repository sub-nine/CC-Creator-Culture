package com.sub9.productservice.leaderboard.application.model;


import java.util.UUID;

public record ProductViewCount(UUID productId, long viewCount) {}