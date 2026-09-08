package com.sub9.userservice.notification.application.service;

import com.sub9.userservice.notification.application.dto.NotificationEventCommand;
import com.sub9.userservice.notification.domain.model.NotificationEvent;
import com.sub9.userservice.notification.domain.repository.NotificationEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventRegistrationService {

    private final NotificationEventRepository notificationeventrepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean register(NotificationEventCommand notificationeventcommand) {
        var existing = notificationeventrepository.findById(notificationeventcommand.eventId());
        if (existing.isPresent()) {
            return existing.get().getStatus().isRetryable();
        }

        NotificationEvent event = NotificationEvent.received(
                notificationeventcommand.eventId(),
                notificationeventcommand.eventType(),
                notificationeventcommand.sourceService(),
                notificationeventcommand.referenceType(),
                notificationeventcommand.referenceId()
        );
        return notificationeventrepository.saveNew(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID eventId, Exception exception) {
        notificationeventrepository.findById(eventId).ifPresent(event -> {
            event.markFailed(exception.getMessage());
            notificationeventrepository.save(event);
        });
    }
}