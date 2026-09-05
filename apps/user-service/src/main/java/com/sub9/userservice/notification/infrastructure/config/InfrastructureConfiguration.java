package com.sub9.userservice.notification.infrastructure.config;

import com.sub9.userservice.notification.infrastructure.external.slack.SlackProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(SlackProperties.class)
public class InfrastructureConfiguration {

    @Bean
    @ConditionalOnMissingBean(WebClient.class)
    WebClient notificationWebClient() {
        return WebClient.builder().build();
    }
}
