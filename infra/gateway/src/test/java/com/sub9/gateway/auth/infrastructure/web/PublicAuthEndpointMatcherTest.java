package com.sub9.gateway.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

@DisplayName("공개 인증 경로 판별")
class PublicAuthEndpointMatcherTest {

    private final PublicAuthEndpointMatcher matcher = new PublicAuthEndpointMatcher();

    @ParameterizedTest
    @MethodSource("publicEndpoints")
    @DisplayName("공개 인증 API의 Method와 Path가 정확히 일치하면 허용한다")
    void when_method_and_path_match_public_endpoint_then_returns_true(
            HttpMethod method,
            String path) {
        var request = MockServerHttpRequest.method(method, path).build();

        assertThat(matcher.matches(request)).isTrue();
    }

    @Test
    @DisplayName("공개 Path라도 HTTP Method가 다르면 보호 요청으로 판별한다")
    void when_method_does_not_match_then_returns_false() {
        var request = MockServerHttpRequest.get("/api/v1/auth/login").build();

        assertThat(matcher.matches(request)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    @DisplayName("로그아웃과 유사하거나 하위인 Path는 보호 요청으로 판별한다")
    void when_path_is_not_exact_public_endpoint_then_returns_false(String path) {
        var request = MockServerHttpRequest.post(path).build();

        assertThat(matcher.matches(request)).isFalse();
    }

    @Test
    @DisplayName("Query Parameter는 공개 Path 판별에 영향을 주지 않는다")
    void when_public_endpoint_has_query_parameters_then_returns_true() {
        var request = MockServerHttpRequest
                .post("/api/v1/auth/login?source=web")
                .build();

        assertThat(matcher.matches(request)).isTrue();
    }

    private static Stream<Arguments> publicEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/v1/auth/signup/customer"),
                Arguments.of(HttpMethod.POST, "/api/v1/auth/signup/creator"),
                Arguments.of(HttpMethod.POST, "/api/v1/auth/login"),
                Arguments.of(HttpMethod.POST, "/api/v1/auth/reissue"));
    }

    private static Stream<String> protectedEndpoints() {
        return Stream.of(
                "/api/v1/auth/logout",
                "/api/v1/auth/login/extra",
                "/api/v1/auth/login/",
                "/api/v1/auth",
                "/api/v1/auth%2Flogin");
    }
}
