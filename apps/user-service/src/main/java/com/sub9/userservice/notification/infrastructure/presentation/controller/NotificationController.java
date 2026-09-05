package com.sub9.userservice.notification.infrastructure.presentation.controller;

import com.sub9.userservice.notification.application.dto.AuthenticatedUser;
import com.sub9.userservice.notification.application.service.NotificationQueryService;
import com.sub9.userservice.notification.infrastructure.presentation.response.ApiResponse;
import com.sub9.userservice.notification.infrastructure.presentation.response.NotificationPageResponse;
import com.sub9.userservice.notification.infrastructure.presentation.response.NotificationResponse;
import com.sub9.userservice.notification.infrastructure.presentation.response.ReadAllResponse;
import com.sub9.userservice.notification.infrastructure.presentation.response.UnreadCountResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final NotificationQueryService notificationQueryService;

    @GetMapping("/")
    public ApiResponse<NotificationPageResponse> findAll(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(USER_ROLE_HEADER) String userRole,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(150) int size
    ) {
        AuthenticatedUser requester = new AuthenticatedUser(userId, userRole);
        return ApiResponse.success(
                "Notification list retrieved.",
                NotificationPageResponse.from(
                        notificationQueryService.findMyNotifications(
                                requester,
                                page,
                                size)
                )
        );
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(USER_ROLE_HEADER) String userRole
    ) {
        AuthenticatedUser requester = new AuthenticatedUser(userId, userRole);
        return ApiResponse.success(
                "Unread notification count retrieved.",
                new UnreadCountResponse(notificationQueryService.countUnread(requester))
        );
    }

    @PatchMapping("/read-all")
    public ApiResponse<ReadAllResponse> readAll(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(USER_ROLE_HEADER) String userRole
    ) {
        AuthenticatedUser requester = new AuthenticatedUser(userId, userRole);
        return ApiResponse.success(
                "All notifications marked as read.",
                new ReadAllResponse(notificationQueryService.markAllRead(requester))
        );
    }

    @GetMapping("/{notificationId}")
    public ApiResponse<NotificationResponse> findOne(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(USER_ROLE_HEADER) String userRole,
            @PathVariable UUID notificationId
    ) {
        AuthenticatedUser requester = new AuthenticatedUser(userId, userRole);
        return ApiResponse.success(
                "Notification retrieved.",
                NotificationResponse.from(
                        notificationQueryService.findMyNotification(requester, notificationId)
                )
        );
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> read(
            @RequestHeader(USER_ID_HEADER) UUID userId,
            @RequestHeader(USER_ROLE_HEADER) String userRole,
            @PathVariable UUID notificationId
    ) {
        AuthenticatedUser requester = new AuthenticatedUser(userId, userRole);
        return ApiResponse.success(
                "Notification marked as read.",
                NotificationResponse.from(
                        notificationQueryService.markRead(requester, notificationId)
                )
        );
    }
}