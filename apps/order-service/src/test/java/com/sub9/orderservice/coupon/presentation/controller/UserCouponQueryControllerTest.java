package com.sub9.orderservice.coupon.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.orderservice.coupon.application.service.UserCouponQueryService;
import com.sub9.orderservice.coupon.domain.model.UserCouponStatus;
import com.sub9.orderservice.coupon.presentation.response.UserCouponResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserCouponQueryController.class)
@Import({GlobalExceptionHandler.class, UserCouponQueryControllerTest.TestSecurityConfig.class})
@DisplayName("내 쿠폰 조회 API")
class UserCouponQueryControllerTest {

    private static final UUID USER_ID = UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID USER_COUPON_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000002");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserCouponQueryService userCouponQueryService;

    @Test
    @DisplayName("사용자 헤더와 상태 조건으로 내 쿠폰 목록을 조회한다")
    void finds_my_coupons_with_status_filter() throws Exception {
        when(userCouponQueryService.findAll(eq(USER_ID), eq(UserCouponStatus.ISSUED), any()))
                .thenReturn(new PageImpl<>(List.of(response())));

        mockMvc.perform(get("/api/v1/user-coupons")
                        .header("X-User-Id", USER_ID)
                        .param("status", "ISSUED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].userCouponId")
                        .value(USER_COUPON_ID.toString()))
                .andExpect(jsonPath("$.data.content[0].status").value("ISSUED"))
                .andExpect(jsonPath("$.data.content[0].expired").value(false));
    }

    @Test
    @DisplayName("사용자 헤더가 없으면 400을 반환한다")
    void rejects_missing_user_header() throws Exception {
        mockMvc.perform(get("/api/v1/user-coupons"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0002"));
    }

    @Test
    @DisplayName("지원하지 않는 쿠폰 상태이면 400을 반환한다")
    void rejects_unknown_status() throws Exception {
        mockMvc.perform(get("/api/v1/user-coupons")
                        .header("X-User-Id", USER_ID)
                        .param("status", "EXPIRED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0002"));
    }

    private UserCouponResponse response() {
        return new UserCouponResponse(
                USER_COUPON_ID, UUID.randomUUID(), "내 쿠폰", 10,
                UserCouponStatus.ISSUED, false,
                Instant.parse("2026-09-06T00:00:00Z"), null,
                Instant.parse("2026-09-08T00:00:00Z"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }
    }
}
