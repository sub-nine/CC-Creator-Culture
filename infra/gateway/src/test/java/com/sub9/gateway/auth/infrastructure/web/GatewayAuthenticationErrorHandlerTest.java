package com.sub9.gateway.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sub9.gateway.auth.domain.exception.AuthenticationServiceUnavailableException;
import com.sub9.gateway.auth.domain.exception.InvalidAccessTokenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Gateway 인증 오류 응답 처리")
class GatewayAuthenticationErrorHandlerTest {

    private final GatewayAuthenticationErrorHandler errorHandler =
            new GatewayAuthenticationErrorHandler(new ObjectMapper());

    @Test
    @DisplayName("유효하지 않은 Access Token은 인증 실패 응답을 반환한다")
    void when_access_token_is_invalid_returns_unauthorized_response() {
        MockServerWebExchange exchange = createExchange();

        StepVerifier.create(errorHandler.handle(exchange, new InvalidAccessTokenException()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .isEqualTo(
                        "{\"errorCode\":\"COMMON_0007\",\"message\":\"인증이 필요합니다.\",\"errors\":[]}");
    }

    @Test
    @DisplayName("Redis 장애는 서비스 사용 불가 응답을 반환한다")
    void when_redis_is_unavailable_returns_service_unavailable_response() {
        MockServerWebExchange exchange = createExchange();
        RuntimeException redisCause = new RuntimeException("Redis connection failed");

        StepVerifier.create(
                        errorHandler.handle(
                                exchange, new AuthenticationServiceUnavailableException(redisCause)))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .isEqualTo(
                        "{\"errorCode\":\"COMMON_0009\",\"message\":\"서비스를 일시적으로 사용할 수 없습니다.\",\"errors\":[]}")
                .doesNotContain("Redis connection failed");
    }

    @Test
    @DisplayName("인증과 관련 없는 예외는 처리하지 않고 전파한다")
    void when_exception_is_not_authentication_related_propagates_exception() {
        MockServerWebExchange exchange = createExchange();
        IllegalStateException exception = new IllegalStateException("unexpected");

        StepVerifier.create(errorHandler.handle(exchange, exception))
                .expectErrorSatisfies(actual -> assertThat(actual).isSameAs(exception))
                .verify();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private MockServerWebExchange createExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders").build());
    }
}
