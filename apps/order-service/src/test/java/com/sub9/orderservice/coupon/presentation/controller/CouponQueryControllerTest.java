package com.sub9.orderservice.coupon.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.orderservice.coupon.application.service.CouponQueryService;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.presentation.response.CouponResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CouponQueryController.class)
@Import({GlobalExceptionHandler.class, CouponQueryControllerTest.TestSecurityConfig.class})
@DisplayName("쿠폰 조회 API")
class CouponQueryControllerTest {
    private static final UUID COUPON_ID = UUID.fromString("01990a00-0000-7000-8000-000000000001");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CouponQueryService couponQueryService;

    @Test
    @DisplayName("쿠폰 목록을 기본 페이징 조건으로 조회한다")
    void when_coupon_list_is_requested_paged_response_is_returned() throws Exception {
        when(couponQueryService.findAll(any())).thenReturn(new PageImpl<>(java.util.List.of(response())));

        mockMvc.perform(get("/api/v1/coupons").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].couponId").value(COUPON_ID.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("쿠폰 상세를 조회한다")
    void when_coupon_detail_is_requested_detail_response_is_returned() throws Exception {
        when(couponQueryService.findById(COUPON_ID)).thenReturn(response());

        mockMvc.perform(get("/api/v1/coupons/{couponId}", COUPON_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.couponName").value("조회 쿠폰"));
    }

    @Test
    @DisplayName("쿠폰이 없으면 쿠폰 오류 코드와 404를 반환한다")
    void when_coupon_does_not_exist_not_found_response_is_returned() throws Exception {
        when(couponQueryService.findById(COUPON_ID))
                .thenThrow(new BusinessException(CouponErrorCode.COUPON_NOT_FOUND));

        mockMvc.perform(get("/api/v1/coupons/{couponId}", COUPON_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COUPON_0001"));
    }

    private CouponResponse response() {
        return new CouponResponse(COUPON_ID, "조회 쿠폰", 10, 100, 0,
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
