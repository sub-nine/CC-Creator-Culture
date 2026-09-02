package com.sub9.common.config;

import com.sub9.common.identifier.UuidV7Generator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class CommonIdentifierAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    UuidV7Generator uuidV7Generator() {
        return new UuidV7Generator();
    }
}
