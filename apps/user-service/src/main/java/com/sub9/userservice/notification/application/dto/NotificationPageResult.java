package com.sub9.userservice.notification.application.dto;

import java.util.List;

public record NotificationPageResult(
        List<NotificationView> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
