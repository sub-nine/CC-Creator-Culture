package com.sub9.userservice.notification.infrastructure.config;

import com.sub9.userservice.notification.infrastructure.external.slack.SlackProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(SlackProperties.class)
public class InfrastructureConfiguration {
}
