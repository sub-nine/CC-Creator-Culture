package com.sub9.userservice.notification.domain.repository;

import java.util.List;
import java.util.UUID;

public interface FollowerLookup {

    List<UUID> findFollowerIds(UUID creatorId);
}
