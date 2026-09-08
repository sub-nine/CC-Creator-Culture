package com.sub9.userservice.notification.infrastructure.config;

import com.sub9.userservice.notification.domain.repository.FollowerLookup;
import com.sub9.userservice.notification.domain.repository.WishlistLookup;
import com.sub9.userservice.notification.domain.service.NotificationMessageFactory;
import com.sub9.userservice.notification.domain.service.RecipientResolver;
import com.sub9.userservice.notification.domain.service.SensitiveDataMasker;
import com.sub9.userservice.notification.domain.service.SlackPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DomainServiceConfiguration {

    @Bean
    RecipientResolver recipientResolver(
            FollowerLookup followerLookup,
            WishlistLookup wishlistLookup
    ) {
        return new RecipientResolver(followerLookup, wishlistLookup);
    }

    @Bean
    NotificationMessageFactory notificationMessageFactory() {
        return new NotificationMessageFactory();
    }

    @Bean
    SlackPolicy slackPolicy() {
        return new SlackPolicy();
    }

    @Bean
    SensitiveDataMasker sensitiveDataMasker() {
        return new SensitiveDataMasker();
    }

    @Bean
    @ConditionalOnMissingBean(FollowerLookup.class)
    FollowerLookup followerLookup() {
        return creatorId -> List.of();
    }

    @Bean
    @ConditionalOnMissingBean(WishlistLookup.class)
    WishlistLookup wishlistLookup() {
        return productId -> List.of();
    }
}
