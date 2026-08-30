package com.sub9.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.common.exception.GlobalExceptionHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@DisplayName("공통 MVC 예외 처리 auto-configuration")
class CommonWebExceptionAutoConfigurationTest {

    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonWebExceptionAutoConfiguration.class));

    @Test
    @DisplayName("Servlet 웹 애플리케이션에 전역 예외 처리기를 등록한다")
    void when_servlet_web_application_starts_then_registers_global_exception_handler() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(GlobalExceptionHandler.class));
    }

    @Test
    @DisplayName("대체 전역 예외 처리기 bean이 있으면 자동 등록하지 않는다")
    void when_custom_global_exception_handler_exists_then_auto_configuration_backs_off() {
        contextRunner
                .withBean(CustomGlobalExceptionHandler.class, CustomGlobalExceptionHandler::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
                    assertThat(context.getBean(GlobalExceptionHandler.class))
                            .isInstanceOf(CustomGlobalExceptionHandler.class);
                });
    }

    @Test
    @DisplayName("서비스의 독립 전역 예외 처리기와 bean 이름이 충돌하지 않는다")
    void when_independent_global_exception_handler_exists_then_context_starts_without_name_collision() {
        contextRunner
                .withBean("globalExceptionHandler", IndependentGlobalExceptionHandler.class,
                        IndependentGlobalExceptionHandler::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
                    assertThat(context).hasBean("globalExceptionHandler");
                    assertThat(context).hasBean(
                            CommonWebExceptionAutoConfiguration.COMMON_GLOBAL_EXCEPTION_HANDLER_BEAN_NAME);
                });
    }

    @Test
    @DisplayName("웹 애플리케이션이 아니면 전역 예외 처리기를 등록하지 않는다")
    void when_application_is_not_web_then_does_not_register_global_exception_handler() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonWebExceptionAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class));
    }

    @Test
    @DisplayName("공통 MVC 예외 처리 auto-configuration을 imports에 등록한다")
    void auto_configuration_imports_contains_common_web_exception_auto_configuration() throws IOException {
        ClassLoader classLoader = CommonWebExceptionAutoConfigurationTest.class.getClassLoader();

        try (var inputStream = classLoader.getResourceAsStream(AUTO_CONFIGURATION_IMPORTS)) {
            assertThat(inputStream).isNotNull();
            String imports = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(imports.lines())
                    .contains(CommonWebExceptionAutoConfiguration.class.getName());
        }
    }

    static class CustomGlobalExceptionHandler extends GlobalExceptionHandler {
    }

    @RestControllerAdvice
    static class IndependentGlobalExceptionHandler {
    }
}
