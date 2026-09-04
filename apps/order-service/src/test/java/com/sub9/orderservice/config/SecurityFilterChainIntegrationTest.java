package com.sub9.orderservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.CommonErrorCode;
import com.sub9.orderservice.common.security.GatewayAuthenticationPrincipal;
import com.sub9.orderservice.common.security.GatewayHeaderAuthenticationFilter;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityFilterChainIntegrationTest.TestOrderController.class)
@ActiveProfiles("dev")
@Import({
        SecurityConfig.class,
        NonProductionActuatorSecurityConfig.class,
        SecurityFilterChainIntegrationTest.TestOrderController.class
})
@DisplayName("Order Security Filter Chain")
class SecurityFilterChainIntegrationTest {

    private static final UUID USER_ID =
            UUID.fromString("0198f2a0-76c0-7000-8000-000000000001");
    private static final UUID TOKEN_ID =
            UUID.fromString("0198f2a0-76c0-7000-8000-000000000002");
    private static final long EXPIRES_AT = 1_788_400_000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Test
    @DisplayName("CUSTOMER 내부 헤더로 주문을 생성하면 인증 사용자를 컨트롤러에 전달한다")
    void customer_내부_헤더로_주문을_생성하면_인증_사용자를_컨트롤러에_전달한다() throws Exception {
        mockMvc.perform(주문_요청("CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(content().string(USER_ID.toString()));
    }

    @Test
    @DisplayName("내부 인증 헤더 없이 주문을 생성하면 COMMON_0007과 401을 반환한다")
    void 내부_인증_헤더_없이_주문을_생성하면_인증_오류를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.UNAUTHORIZED.code()));
    }

    @Test
    @DisplayName("잘못된 역할 헤더로 주문을 생성하면 COMMON_0007과 401을 반환한다")
    void 잘못된_역할_헤더로_주문을_생성하면_인증_오류를_반환한다() throws Exception {
        mockMvc.perform(주문_요청("customer"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.UNAUTHORIZED.code()));
    }

    @Test
    @DisplayName("CUSTOMER가 아닌 사용자가 주문을 생성하면 COMMON_0008과 403을 반환한다")
    void customer가_아닌_사용자가_주문을_생성하면_권한_오류를_반환한다() throws Exception {
        mockMvc.perform(주문_요청("CREATOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.FORBIDDEN.code()));
    }

    @Test
    @DisplayName("local과 dev의 Actuator 요청에는 전용 체인만 적용한다")
    void local과_dev의_actuator_요청에는_전용_체인만_적용한다() throws Exception {
        assertThat(filterChainProxy.getFilters("/actuator/health"))
                .noneMatch(GatewayHeaderAuthenticationFilter.class::isInstance);
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("일반 API 요청에는 Gateway 내부 헤더 인증 필터를 적용한다")
    void 일반_api_요청에는_gateway_내부_헤더_인증_필터를_적용한다() {
        assertThat(filterChainProxy.getFilters("/api/v1/orders"))
                .anyMatch(GatewayHeaderAuthenticationFilter.class::isInstance);
    }

    private MockHttpServletRequestBuilder 주문_요청(String role) {
        return post("/api/v1/orders")
                .header(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, USER_ID)
                .header(GatewayHeaderAuthenticationFilter.USER_ROLE_HEADER, role)
                .header(GatewayHeaderAuthenticationFilter.TOKEN_ID_HEADER, TOKEN_ID)
                .header(GatewayHeaderAuthenticationFilter.TOKEN_EXPIRES_AT_HEADER, EXPIRES_AT);
    }

    @RestController
    static class TestOrderController {

        @GetMapping("/actuator/health")
        String health() {
            return "UP";
        }

        @PostMapping("/api/v1/orders")
        String createOrder(@AuthenticationPrincipal GatewayAuthenticationPrincipal principal) {
            return principal.userId().toString();
        }
    }
}
