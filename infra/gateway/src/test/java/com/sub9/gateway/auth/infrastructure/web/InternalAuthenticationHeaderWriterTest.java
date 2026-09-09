package com.sub9.gateway.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.gateway.auth.domain.model.AccessTokenClaims;
import com.sub9.gateway.auth.domain.model.GatewayUserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

@DisplayName("내부 인증 헤더 작성")
class InternalAuthenticationHeaderWriterTest {

    private static final UUID USER_ID =
            UUID.fromString("01992d35-8600-7000-8000-000000000001");
    private static final UUID TOKEN_ID =
            UUID.fromString("01992d35-8600-7000-8000-000000000002");
    private static final Instant ISSUED_AT = Instant.parse("2026-09-09T04:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(1800);

    private final InternalAuthenticationHeaderWriter writer =
            new InternalAuthenticationHeaderWriter();

    @Test
    @DisplayName("검증된 Access Token Claim을 네 내부 인증 헤더로 작성한다")
    void when_claims_are_verified_then_writes_internal_authentication_headers() {
        var request = MockServerHttpRequest.get("/api/v1/orders").build();
        var claims = new AccessTokenClaims(
                USER_ID, GatewayUserRole.CUSTOMER, TOKEN_ID, ISSUED_AT, EXPIRES_AT);

        var authenticated = writer.write(request, claims);

        assertThat(authenticated.getHeaders().getFirst("X-User-Id"))
                .isEqualTo(USER_ID.toString());
        assertThat(authenticated.getHeaders().getFirst("X-User-Role"))
                .isEqualTo("CUSTOMER");
        assertThat(authenticated.getHeaders().getFirst("X-Token-Id"))
                .isEqualTo(TOKEN_ID.toString());
        assertThat(authenticated.getHeaders().getFirst("X-Token-Expires-At"))
                .isEqualTo(Long.toString(EXPIRES_AT.getEpochSecond()));
    }

    @Test
    @DisplayName("기존 내부 인증 헤더를 검증된 값 하나로 덮어쓴다")
    void when_request_contains_existing_headers_then_replaces_them_with_verified_values() {
        var request = MockServerHttpRequest.get("/api/v1/orders")
                .header("X-User-Id", "spoofed-user", "another-user")
                .header("X-User-Role", "MASTER")
                .build();
        var claims = new AccessTokenClaims(
                USER_ID, GatewayUserRole.CUSTOMER, TOKEN_ID, ISSUED_AT, EXPIRES_AT);

        var authenticated = writer.write(request, claims);

        assertThat(authenticated.getHeaders().get("X-User-Id"))
                .containsExactly(USER_ID.toString());
        assertThat(authenticated.getHeaders().get("X-User-Role"))
                .containsExactly("CUSTOMER");
    }
}
