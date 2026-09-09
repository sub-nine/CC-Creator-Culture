package com.sub9.gateway.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

@DisplayName("외부 인증 헤더 제거")
class InternalAuthenticationHeaderSanitizerTest {

    private final InternalAuthenticationHeaderSanitizer sanitizer =
            new InternalAuthenticationHeaderSanitizer();

    @Test
    @DisplayName("외부 요청의 모든 내부 인증 헤더를 제거한다")
    void when_request_contains_internal_authentication_headers_then_removes_all() {
        var request = MockServerHttpRequest.get("/api/v1/orders")
                .header("X-User-Id", "spoofed-user")
                .header("X-User-Role", "MASTER")
                .header("X-Token-Id", "spoofed-token")
                .header("X-Token-Expires-At", "9999999999")
                .build();

        var sanitized = sanitizer.sanitize(request);

        assertThat(sanitized.getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(sanitized.getHeaders().getFirst("X-User-Role")).isNull();
        assertThat(sanitized.getHeaders().getFirst("X-Token-Id")).isNull();
        assertThat(sanitized.getHeaders().getFirst("X-Token-Expires-At")).isNull();
    }

    @Test
    @DisplayName("대소문자가 다른 내부 인증 헤더도 제거한다")
    void when_header_names_have_different_case_then_removes_them() {
        var request = MockServerHttpRequest.get("/api/v1/orders")
                .header("x-user-id", "spoofed-user")
                .header("X-USER-ROLE", "MASTER")
                .header("x-token-id", "spoofed-token")
                .header("X-TOKEN-EXPIRES-AT", "9999999999")
                .build();

        var sanitized = sanitizer.sanitize(request);

        assertThat(sanitized.getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(sanitized.getHeaders().getFirst("X-User-Role")).isNull();
        assertThat(sanitized.getHeaders().getFirst("X-Token-Id")).isNull();
        assertThat(sanitized.getHeaders().getFirst("X-Token-Expires-At")).isNull();
    }

    @Test
    @DisplayName("Authorization과 일반 요청 헤더는 유지한다")
    void when_request_is_sanitized_then_preserves_non_internal_headers() {
        var request = MockServerHttpRequest.get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .header("X-Request-Id", "request-id")
                .header("X-User-Id", "spoofed-user")
                .build();

        var sanitized = sanitizer.sanitize(request);

        assertThat(sanitized.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer access-token");
        assertThat(sanitized.getHeaders().getFirst("X-Request-Id"))
                .isEqualTo("request-id");
    }

    @Test
    @DisplayName("원본 요청은 변경하지 않고 정제된 새 요청을 반환한다")
    void when_request_is_sanitized_then_does_not_mutate_original_request() {
        var request = MockServerHttpRequest.get("/api/v1/orders")
                .header("X-User-Id", "spoofed-user")
                .build();

        var sanitized = sanitizer.sanitize(request);

        assertThat(sanitized).isNotSameAs(request);
        assertThat(request.getHeaders().getFirst("X-User-Id")).isEqualTo("spoofed-user");
        assertThat(sanitized.getHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    @DisplayName("내부 인증 헤더가 없어도 요청을 정상적으로 정제한다")
    void when_request_has_no_internal_authentication_headers_then_returns_request_copy() {
        var request = MockServerHttpRequest.get("/api/v1/orders")
                .header("X-Request-Id", "request-id")
                .build();

        var sanitized = sanitizer.sanitize(request);

        assertThat(sanitized.getHeaders().getFirst("X-Request-Id")).isEqualTo("request-id");
    }
}
