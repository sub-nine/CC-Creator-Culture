package com.sub9.userservice.notification.application.service;

import com.sub9.userservice.notification.application.dto.AuthenticatedUser;
import com.sub9.userservice.notification.application.dto.NotificationPageResult;
import com.sub9.userservice.notification.application.dto.NotificationView;
import com.sub9.userservice.notification.application.exception.NotificationNotFoundException;
import com.sub9.userservice.notification.domain.model.Notification;
import com.sub9.userservice.notification.domain.model.NotificationPage;
import com.sub9.userservice.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    public NotificationPageResult findMyNotifications(
                        AuthenticatedUser requester,
                        int page,
                        int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 150);
        NotificationPage result = notificationRepository.findPageByUserId(
                                        requester.userId(),
                                        safePage,
                                        safeSize
        );

        List<NotificationView> views = result.content().stream()
                .map(NotificationView::from)
                .toList();

        int totalPages = (int) Math.ceil((double) result.totalElements() / safeSize);

        return new NotificationPageResult(
                views,
                safePage,
                safeSize,
                result.
                totalElements(),
                totalPages
        );
    }


    public NotificationView findMyNotification(
                        AuthenticatedUser requester,
                        UUID notificationId
    ) {
        return NotificationView.from(
                findOwned(requester.userId(), notificationId));
    }

    public long countUnread(AuthenticatedUser requester) {
        return notificationRepository.countUnread(requester.userId());
    }

    @Transactional
    public NotificationView markRead(
            AuthenticatedUser requester,
            UUID notificationId
    ) {
        Notification notification = findOwned(requester.userId(), notificationId);
        notification.markRead();
        notificationRepository.save(notification);
        return NotificationView.from(notification);
    }

    @Transactional
    public int markAllRead(
            AuthenticatedUser requester
    ) {
        return notificationRepository.markAllRead(
                requester.userId(), Instant.now()
        );
    }

    private Notification findOwned(UUID userId, UUID notificationId) {
        return notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(NotificationNotFoundException::new);
    }
}
