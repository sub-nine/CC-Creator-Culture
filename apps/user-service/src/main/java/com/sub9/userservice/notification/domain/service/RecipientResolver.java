package com.sub9.userservice.notification.domain.service;

import com.sub9.userservice.notification.domain.model.NotificationContext;
import com.sub9.userservice.notification.domain.repository.FollowerLookup;
import com.sub9.userservice.notification.domain.repository.WishlistLookup;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
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

            case ORDER_CREATED -> requiredSellers(context.sellerUserIds());
            case ORDER_CANCELLED -> {
                List<UUID> recipients = new ArrayList<>();
                recipients.add(required(context.buyerId(), "buyerId"));
                recipients.addAll(context.sellerUserIds());
                yield recipients;
            }
            case PAYMENT_PAID, PAYMENT_FAILED -> List.of(
                    required(context.buyerId(), "buyerId")
            );
        };

        return candidates.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<UUID> requiredSellers(List<UUID> sellerUserIds) {
        if (sellerUserIds == null || sellerUserIds.isEmpty()) {
            throw new IllegalArgumentException("sellerUserIds is required for this event type");
        }
        return sellerUserIds;
    }

    private UUID required(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required for this event type");
        }
        return value;
    }
}
