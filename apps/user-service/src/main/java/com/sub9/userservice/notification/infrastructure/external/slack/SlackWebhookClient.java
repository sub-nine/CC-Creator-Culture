package com.sub9.userservice.notification.infrastructure.external.slack;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;

@Component
public class SlackWebhookClient {

    private final WebClient webClient;
    private final SlackProperties properties;

    public SlackWebhookClient(
            WebClient
                    .Builder builder,
             SlackProperties properties) {
        this.webClient = builder.build();
        this.properties = properties;
    }

    /** destination에 연결된 URL로 text JSON을 보내고 최대 5초 기다립니다. */
    public void send(
            String destination,
            String message)
    {
        String webhookUrl = properties.webhookUrl(destination);
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new SlackDeliveryException(
                    "SLACK_WEBHOOK_NOT_CONFIGURED",
                    "Webhook URL is not configured for destination=" + destination
            );
        }

        try {
            webClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("text", message))
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(5));
        } catch (WebClientResponseException exception) {
            throw new SlackDeliveryException(
                    "SLACK_HTTP_" + exception.getStatusCode().value(),
                    "Slack returned HTTP " + exception.getStatusCode().value()
            );
        } catch (WebClientRequestException exception) {
            throw new SlackDeliveryException("SLACK_CONNECTION_FAILED", exception.getMessage());
        } catch (RuntimeException exception) {
            throw new SlackDeliveryException("SLACK_TIMEOUT_OR_UNKNOWN", exception.getMessage());
        }
    }
}
