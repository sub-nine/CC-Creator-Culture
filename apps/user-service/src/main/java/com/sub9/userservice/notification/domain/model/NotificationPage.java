package com.sub9.userservice.notification.domain.model;

import java.util.List;

public record NotificationPage(
        List<Notification> content,
        long totalElements
) {
}