package com.sub9.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.common.identifier.UuidV7Generator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("공통 식별자 auto-configuration")
class CommonIdentifierAutoConfigurationTest {

    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonIdentifierAutoConfiguration.class));

    @Test
    @DisplayName("UUID v7 생성기를 Bean으로 등록한다")
    void when_application_starts_then_registers_uuid_v7_generator() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(UuidV7Generator.class));
    }

    @Test
    @DisplayName("대체 UUID v7 생성기 Bean이 있으면 자동 등록하지 않는다")
    void when_custom_uuid_v7_generator_exists_then_auto_configuration_backs_off() {
        UuidV7Generator customGenerator = new UuidV7Generator();

        contextRunner
                .withBean(UuidV7Generator.class, () -> customGenerator)
                .run(context -> {
                    assertThat(context).hasSingleBean(UuidV7Generator.class);
                    assertThat(context.getBean(UuidV7Generator.class)).isSameAs(customGenerator);
                });
    }

    @Test
    @DisplayName("공통 식별자 auto-configuration을 imports에 등록한다")
    void auto_configuration_imports_contains_common_identifier_auto_configuration() throws IOException {
        ClassLoader classLoader = CommonIdentifierAutoConfigurationTest.class.getClassLoader();

        try (var inputStream = classLoader.getResourceAsStream(AUTO_CONFIGURATION_IMPORTS)) {
            assertThat(inputStream).isNotNull();
            String imports = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(imports.lines())
                    .contains(CommonIdentifierAutoConfiguration.class.getName());
        }
    }
}
