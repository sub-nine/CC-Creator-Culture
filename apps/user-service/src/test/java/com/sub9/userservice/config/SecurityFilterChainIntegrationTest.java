package com.sub9.userservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.userservice.auth.application.service.LogoutService;
import com.sub9.userservice.auth.infrastructure.security.GatewayHeaderAuthenticationFilter;
import com.sub9.userservice.auth.presentation.controller.LogoutController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(
        controllers = LogoutController.class,
        properties = "auth.jwt.secret=test-secret")
@ActiveProfiles("dev")
@Import({SecurityConfig.class, NonProductionActuatorSecurityConfig.class})
@DisplayName("Security Filter Chain 분리")
class SecurityFilterChainIntegrationTest {

    @Autowired
    private FilterChainProxy filterChainProxy;

    @MockitoBean
    private LogoutService logoutService;

    @Test
    @DisplayName("local/dev Actuator 요청은 전용 체인만 적용한다")
    void applies_actuator_chain_before_application_chain() {
        assertThat(filterChainProxy.getFilters("/actuator/health"))
                .noneMatch(GatewayHeaderAuthenticationFilter.class::isInstance);
    }

    @Test
    @DisplayName("일반 API 요청은 Gateway 내부 헤더 인증 필터를 적용한다")
    void applies_application_chain_to_api_request() {
        assertThat(filterChainProxy.getFilters("/api/v1/auth/logout"))
                .anyMatch(GatewayHeaderAuthenticationFilter.class::isInstance);
    }
}
