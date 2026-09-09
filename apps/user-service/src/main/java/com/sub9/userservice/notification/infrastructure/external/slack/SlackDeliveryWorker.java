package com.sub9.userservice.notification.infrastructure.external.slack;

import com.sub9.userservice.notification.domain.model.SlackDelivery;
import com.sub9.userservice.notification.domain.repository.SlackDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
public class SlackDeliveryWorker {
    private static final int BATCH_SIZE = 50;

    private final SlackDeliveryRepository repository;
    private final SlackWebhookClient client;
    private final SlackProperties properties;

    @Scheduled(fixedDelayString = "${app.slack.worker-delay-ms:5000}")
    public void dispatchPending() {
        if (!properties.isEnabled()) {
            return;
        }
        repository
                .findPendingReady(Instant.now(), BATCH_SIZE)
                .forEach(this::dispatchOne);
    }

    void dispatchOne(SlackDelivery delivery) {
        delivery.markSending();
        repository.save(delivery);

        try {
            client.send(
                    delivery.getDestination(),
                    delivery.getSlackMessage()
            );
            delivery.markSent();
        } catch (SlackDeliveryException exception) {
            delivery.markSendFailed(
                    exception.getCode(),
                    exception.getMessage(),
                    properties.getMaxRetries(),
                    Instant.now().plusMillis(properties.getRetryDelayMs())
            );
            log.warn(
                    "Slack delivery failed. id={}, status={}, attemptCount={}, retryCount={}, code={}",
                    delivery.getSlackDeliveryId(),
                    delivery.getStatus(),
                    delivery.getAttemptCount(),
                    delivery.getRetryCount(),
                    exception.getCode()
            );
        }
        repository.save(delivery);
    }
}
