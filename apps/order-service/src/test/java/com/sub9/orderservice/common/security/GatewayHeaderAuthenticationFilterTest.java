package com.sub9.orderservice.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.exception.CommonErrorCode;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("Gateway 내부 헤더 인증 필터")
class GatewayHeaderAuthenticationFilterTest {

    private static final UUID USER_ID =
            UUID.fromString("0198f2a0-76c0-7000-8000-000000000001");
    private static final UUID TOKEN_ID =
            UUID.fromString("0198f2a0-76c0-7000-8000-000000000002");
    private static final long EXPIRES_AT = 1_788_400_000L;

    private final GatewayHeaderAuthenticationFilter filter = new GatewayHeaderAuthenticationFilter(
            (request, response, exception) -> {
                response.setStatus(CommonErrorCode.UNAUTHORIZED.status().value());
                response.getWriter().write(CommonErrorCode.UNAUTHORIZED.code());
            });

    @AfterEach
    void 테스트_후_인증_정보를_정리한다() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("네 내부 헤더가 유효할 때 필터를 실행하면 인증 객체를 설정한다")
    void 네_내부_헤더가_유효할_때_필터를_실행하면_인증_객체를_설정한다() throws Exception {
        MockHttpServletRequest request = 유효한_주문_요청();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CUSTOMER");
        assertThat(authentication.getPrincipal()).isEqualTo(new GatewayAuthenticationPrincipal(
                USER_ID,
                GatewayAuthenticationPrincipal.Role.CUSTOMER,
                TOKEN_ID,
                EXPIRES_AT));
    }

    @ParameterizedTest(name = "{0} 누락")
    @ValueSource(strings = {
            GatewayHeaderAuthenticationFilter.USER_ID_HEADER,
            GatewayHeaderAuthenticationFilter.USER_ROLE_HEADER,
            GatewayHeaderAuthenticationFilter.TOKEN_ID_HEADER,
            GatewayHeaderAuthenticationFilter.TOKEN_EXPIRES_AT_HEADER
    })
    @DisplayName("필수 내부 헤더가 누락될 때 필터를 실행하면 COMMON_0007과 401을 반환한다")
    void 필수_내부_헤더가_누락될_때_필터를_실행하면_인증_오류를_반환한다(String headerName)
            throws Exception {
        MockHttpServletRequest request = 유효한_주문_요청();
        request.removeHeader(headerName);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(CommonErrorCode.UNAUTHORIZED.code());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @ParameterizedTest(name = "{0} 형식 오류")
    @MethodSource("잘못된_내부_헤더")
    @DisplayName("내부 헤더 형식이 잘못됐을 때 필터를 실행하면 COMMON_0007과 401을 반환한다")
    void 내부_헤더_형식이_잘못됐을_때_필터를_실행하면_인증_오류를_반환한다(
            String headerName,
            String invalidValue) throws Exception {
        MockHttpServletRequest request = 유효한_주문_요청();
        request.removeHeader(headerName);
        request.addHeader(headerName, invalidValue);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(CommonErrorCode.UNAUTHORIZED.code());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("인증 뒤 요청 처리에서 발생한 예외는 인증 오류로 바꾸지 않는다")
    void 인증_뒤_요청_처리에서_예외가_발생하면_원래_예외를_전달한다() {
        MockHttpServletRequest request = 유효한_주문_요청();
        MockHttpServletResponse response = new MockHttpServletResponse();
        IllegalArgumentException failure = new IllegalArgumentException("요청 처리 실패");

        assertThatThrownBy(() -> filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                    throw failure;
                }))
                .isSameAs(failure);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static Stream<Arguments> 잘못된_내부_헤더() {
        return Stream.of(
                Arguments.of(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, "not-a-uuid"),
                Arguments.of(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, "1-1-1-1-1"),
                Arguments.of(GatewayHeaderAuthenticationFilter.USER_ROLE_HEADER, "UNKNOWN"),
                Arguments.of(GatewayHeaderAuthenticationFilter.TOKEN_ID_HEADER, "not-a-uuid"),
                Arguments.of(GatewayHeaderAuthenticationFilter.TOKEN_ID_HEADER, "1-1-1-1-1"),
                Arguments.of(GatewayHeaderAuthenticationFilter.TOKEN_EXPIRES_AT_HEADER, "not-a-number"));
    }

    private MockHttpServletRequest 유효한_주문_요청() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.setServletPath("/api/v1/orders");
        request.addHeader(GatewayHeaderAuthenticationFilter.USER_ID_HEADER, USER_ID.toString());
        request.addHeader(GatewayHeaderAuthenticationFilter.USER_ROLE_HEADER, "CUSTOMER");
        request.addHeader(GatewayHeaderAuthenticationFilter.TOKEN_ID_HEADER, TOKEN_ID.toString());
        request.addHeader(GatewayHeaderAuthenticationFilter.TOKEN_EXPIRES_AT_HEADER, EXPIRES_AT);
        return request;
    }
}
