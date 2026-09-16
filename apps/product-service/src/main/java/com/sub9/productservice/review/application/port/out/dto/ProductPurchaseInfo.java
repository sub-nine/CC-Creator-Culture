package com.sub9.productservice.review.application.port.out.dto;

import java.util.UUID;

public record ProductPurchaseInfo(UUID productId, boolean purchased) {}
