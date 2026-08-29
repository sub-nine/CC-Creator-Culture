package com.sub9.common.config;

import com.sub9.common.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebExceptionAutoConfiguration {

    static final String COMMON_GLOBAL_EXCEPTION_HANDLER_BEAN_NAME = "commonGlobalExceptionHandler";

    @Bean(name = COMMON_GLOBAL_EXCEPTION_HANDLER_BEAN_NAME)
    @ConditionalOnMissingBean(
            value = GlobalExceptionHandler.class,
            name = COMMON_GLOBAL_EXCEPTION_HANDLER_BEAN_NAME)
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
