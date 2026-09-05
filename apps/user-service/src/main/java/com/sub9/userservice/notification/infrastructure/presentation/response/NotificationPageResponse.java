package com.sub9.userservice.notification.infrastructure.presentation.response;

import com.sub9.userservice.notification.application.dto.NotificationPageResult;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static NotificationPageResponse from(NotificationPageResult result) {
        return new NotificationPageResponse(
                result.content().stream()
                        .map(NotificationResponse::from)
                        .toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }
}