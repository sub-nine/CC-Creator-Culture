package com.sub9.userservice.notification.application.service;

import com.sub9.userservice.notification.application.dto.NotificationEventCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationEventCoordinator {

    private final EventRegistrationService eventRegistrationService;
    private final NotificationEventProcessingService notificationProcessingService;

    public void handle(NotificationEventCommand command) {
        if (!eventRegistrationService.register(command)) {
            log.info("Skipped duplicate notification event. eventId={}", command.eventId());
            return;
        }

        try {
            notificationProcessingService.process(command);
        } catch (RuntimeException exception) {
            eventRegistrationService.markFailed(command.eventId(), exception);
            throw exception;
        }
    }
}
