package com.sub9.userservice.notification.domain.repository;

import java.util.List;
import java.util.UUID;

public interface WishlistLookup {

    List<UUID> findInterestedUserIds(UUID productId);
}
