package com.sub9.orderservice.coupon.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.GlobalExceptionHandler;
import com.sub9.orderservice.coupon.application.dto.IssueDispatchResult;
import com.sub9.orderservice.coupon.application.service.CouponCommandService;
import com.sub9.orderservice.coupon.application.service.CouponIssueService;
import com.sub9.orderservice.coupon.domain.exception.CouponErrorCode;
import com.sub9.orderservice.coupon.infrastructure.redis.CouponRedisStorageException;
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
    private static final UUID USER_COUPON_ID =
            UUID.fromString("01990a00-0000-7000-8000-000000000003");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CouponCommandService couponCommandService;
    @MockitoBean private CouponIssueService couponIssueService;

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

    @Test
    @DisplayName("쿠폰 동기 발급에 성공하면 사용자 쿠폰 식별자와 201을 반환한다")
    void when_coupon_issue_succeeds_created_response_is_returned() throws Exception {
        when(couponIssueService.issue(COUPON_ID, USER_ID))
                .thenReturn(new IssueDispatchResult.Completed(USER_COUPON_ID));

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", COUPON_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("쿠폰이 발급되었습니다."))
                .andExpect(jsonPath("$.data.userCouponId").value(USER_COUPON_ID.toString()));
    }

    @Test
    @DisplayName("발급 요청에 사용자 헤더가 없으면 400을 반환한다")
    void when_coupon_issue_user_header_is_missing_bad_request_is_returned() throws Exception {
        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", COUPON_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0002"));
    }

    @Test
    @DisplayName("발급 기간이 아니면 쿠폰 기간 오류와 400을 반환한다")
    void when_coupon_is_outside_issue_period_bad_request_is_returned() throws Exception {
        assertIssueError(CouponErrorCode.NOT_IN_ISSUE_PERIOD, 400, "COUPON_0002");
    }

    @Test
    @DisplayName("쿠폰이 없으면 쿠폰 조회 오류와 404를 반환한다")
    void when_coupon_does_not_exist_not_found_is_returned() throws Exception {
        assertIssueError(CouponErrorCode.COUPON_NOT_FOUND, 404, "COUPON_0001");
    }

    @Test
    @DisplayName("쿠폰이 품절되면 품절 오류와 409를 반환한다")
    void when_coupon_is_sold_out_conflict_is_returned() throws Exception {
        assertIssueError(CouponErrorCode.SOLD_OUT, 409, "COUPON_0003");
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰이면 중복 오류와 409를 반환한다")
    void when_coupon_is_already_issued_conflict_is_returned() throws Exception {
        assertIssueError(CouponErrorCode.ALREADY_ISSUED, 409, "COUPON_0004");
    }

    @Test
    @DisplayName("Redis 장애가 발생하면 서비스 일시 중단 오류와 503을 반환한다")
    void when_redis_fails_service_unavailable_is_returned() throws Exception {
        when(couponIssueService.issue(COUPON_ID, USER_ID))
                .thenThrow(new CouponRedisStorageException());

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", COUPON_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0009"));
    }

    private void assertIssueError(
            CouponErrorCode errorCode, int expectedStatus, String expectedErrorCode) throws Exception {
        when(couponIssueService.issue(COUPON_ID, USER_ID))
                .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(post("/api/v1/coupons/{couponId}/issue", COUPON_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.errorCode").value(expectedErrorCode));
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
