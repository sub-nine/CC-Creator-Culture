package com.sub9.userservice.notification.domain.service;

import com.sub9.userservice.notification.domain.model.NotificationContext;
import com.sub9.userservice.notification.domain.repository.FollowerLookup;
import com.sub9.userservice.notification.domain.repository.WishlistLookup;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
public class RecipientResolver {

    private final FollowerLookup followerLookup;
    private final WishlistLookup wishlistLookup;

    public List<UUID> resolve(NotificationContext context) {
        List<UUID> candidates = switch (context.eventType()) {
            case PRODUCT_CREATED -> followerLookup.findFollowerIds(
                    required(context.creatorId(), "creatorId")
            );
            case PRODUCT_LOW_STOCK, PRODUCT_SOLD_OUT -> List.of(
                    required(context.creatorId(), "creatorId")
            );
            case PRODUCT_RESTOCKED -> wishlistLookup.findInterestedUserIds(context.referenceId());

            case ORDER_CREATED -> List.of(
                    required(context.sellerId(), "sellerId")
            );
            case ORDER_CANCELLED -> List.of(
                    required(context.buyerId(), "buyerId"),
                    required(context.sellerId(), "sellerId")
            );
            case PAYMENT_PAID, PAYMENT_FAILED -> List.of(
                    required(context.buyerId(), "buyerId")
            );
        };

        return new LinkedHashSet<>(candidates.stream().filter(Objects::nonNull).toList())
                .stream()
                .toList();
    }

    private UUID required(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required for this event type");
        }
        return value;
    }
}
