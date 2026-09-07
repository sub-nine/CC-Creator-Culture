package com.sub9.orderservice.coupon.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.orderservice.coupon.application.service.CouponCommandService;
import com.sub9.orderservice.coupon.presentation.request.CreateCouponRequest;
import com.sub9.orderservice.coupon.presentation.response.CouponResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CouponCommandController.class)
@Import({GlobalExceptionHandler.class, CouponCommandControllerTest.TestSecurityConfig.class})
@DisplayName("쿠폰 명령 API")
class CouponCommandControllerTest {
    private static final UUID USER_ID = UUID.fromString("01990a00-0000-7000-8000-000000000001");
    private static final UUID COUPON_ID = UUID.fromString("01990a00-0000-7000-8000-000000000002");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CouponCommandService couponCommandService;

    @Test
    @DisplayName("유효한 요청과 사용자 헤더로 쿠폰을 생성하면 201을 반환한다")
    void when_valid_request_and_user_header_are_sent_created_response_is_returned() throws Exception {
        when(couponCommandService.create(any(CreateCouponRequest.class), eq(USER_ID))).thenReturn(response());

        mockMvc.perform(post("/api/v1/coupons")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("쿠폰이 생성되었습니다."))
                .andExpect(jsonPath("$.data.couponId").value(COUPON_ID.toString()))
                .andExpect(jsonPath("$.data.issuedQuantity").value(0));
    }

    @Test
    @DisplayName("생성자 헤더가 없으면 400을 반환한다")
    void when_user_header_is_missing_bad_request_is_returned() throws Exception {
        mockMvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0002"));
    }

    @Test
    @DisplayName("할인율과 기간이 잘못되면 검증 오류 400을 반환한다")
    void when_discount_rate_and_period_are_invalid_validation_error_is_returned() throws Exception {
        mockMvc.perform(post("/api/v1/coupons")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"couponName":"쿠폰","discountRate":101,"totalQuantity":100,
                                 "startedAt":"2026-09-07T00:00:00Z","expiredAt":"2026-09-06T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0003"));
    }

    private String validRequest() {
        return """
                {"couponName":"트렌드 15% 할인 쿠폰","discountRate":15,"totalQuantity":100,
                 "startedAt":"2026-09-06T00:00:00Z","expiredAt":"2026-09-07T00:00:00Z"}
                """;
    }

    private CouponResponse response() {
        return new CouponResponse(COUPON_ID, "트렌드 15% 할인 쿠폰", 15, 100, 0,
                Instant.parse("2026-09-06T00:00:00Z"), Instant.parse("2026-09-07T00:00:00Z"));
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
