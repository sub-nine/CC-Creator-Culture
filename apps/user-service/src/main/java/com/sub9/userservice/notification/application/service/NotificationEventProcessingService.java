package com.sub9.userservice.notification.application.service;

import com.sub9.userservice.notification.application.dto.NotificationEventCommand;
import com.sub9.userservice.notification.domain.model.Notification;
import com.sub9.userservice.notification.domain.model.NotificationContext;
import com.sub9.userservice.notification.domain.model.NotificationEvent;
import com.sub9.userservice.notification.domain.model.SlackDelivery;
import com.sub9.userservice.notification.domain.repository.NotificationEventRepository;
import com.sub9.userservice.notification.domain.repository.NotificationRepository;
import com.sub9.userservice.notification.domain.repository.SlackDeliveryRepository;
import com.sub9.userservice.notification.domain.service.NotificationMessageFactory;
import com.sub9.userservice.notification.domain.service.RecipientResolver;
import com.sub9.userservice.notification.domain.service.SensitiveDataMasker;
import com.sub9.userservice.notification.domain.service.SlackPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class NotificationEventProcessingService {

    private final NotificationEventRepository notificationeventRepository;
    private final NotificationRepository notificationRepository;
    private final SlackDeliveryRepository slackDeliveryRepository;
    private final RecipientResolver recipientResolver;
    private final NotificationMessageFactory messageFactory;
    private final SlackPolicy slackPolicy;
    private final SensitiveDataMasker sensitiveDataMasker;

    @Transactional
    public void process(NotificationEventCommand notificationeventcommand) {
        NotificationEvent storedEvent =
                notificationeventRepository
                        .findById(notificationeventcommand.eventId())
                        .orElseThrow(() -> new IllegalStateException("Registered event was not found"));
        storedEvent.markProcessing();

        NotificationContext context = notificationeventcommand.toContext();
        NotificationMessageFactory.Message message = messageFactory.create(context);

        List<UUID> recipients = recipientResolver.resolve(context);
        recipients.forEach(
                recipientId ->
                        createNotificationIfMissing(
                                notificationeventcommand,
                                message,
                                recipientId));

        slackPolicy.destination(context).ifPresent(destination -> {
            if (!slackDeliveryRepository.existsByEventIdAndDestination(
                    notificationeventcommand.eventId(), destination
            )) {
                slackDeliveryRepository.save(SlackDelivery.pending(
                        notificationeventcommand.eventId(),
                        destination,
                        sensitiveDataMasker.mask(buildSlackMessage(notificationeventcommand, message))
                ));
            }
        });

        storedEvent.markCompleted();
        notificationeventRepository.save(storedEvent);
    }

    private void createNotificationIfMissing(
            NotificationEventCommand notificationeventcommand,
            NotificationMessageFactory.Message message,
            UUID recipientId
    ) {
        if (notificationRepository.existsByEventIdAndUserId(notificationeventcommand.eventId(), recipientId)) {
            return;
        }
        notificationRepository.save(Notification.create(
                notificationeventcommand.eventId(),
                recipientId,
                notificationeventcommand.eventType(),
                message.title(),
                message.content(),
                notificationeventcommand.referenceType(),
                notificationeventcommand.referenceId()
        ));
    }

    private String buildSlackMessage(
            NotificationEventCommand notificationeventcommand,
            NotificationMessageFactory.Message message
    ) {
        return "[" + message.title() + "]\n"
                + message.content() + "\n"
                + "eventId: " + notificationeventcommand.eventId() + "\n"
                + "referenceId: " + notificationeventcommand.referenceId();
    }
}
