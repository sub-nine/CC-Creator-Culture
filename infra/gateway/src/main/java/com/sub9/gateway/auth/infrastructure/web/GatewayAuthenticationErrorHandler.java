package com.sub9.gateway.auth.infrastructure.web;

import com.sub9.gateway.auth.domain.exception.AuthenticationServiceUnavailableException;
import com.sub9.gateway.auth.domain.exception.InvalidAccessTokenException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GatewayAuthenticationErrorHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exception instanceof InvalidAccessTokenException) {
            return writeErrorResponse(exchange, GatewayAuthenticationErrorCode.UNAUTHORIZED);
        }

        if (exception instanceof AuthenticationServiceUnavailableException) {
            return writeErrorResponse(
                    exchange, GatewayAuthenticationErrorCode.SERVICE_UNAVAILABLE);
        }

        return Mono.error(exception);
    }

    private Mono<Void> writeErrorResponse(
            ServerWebExchange exchange, GatewayAuthenticationErrorCode errorCode) {
        try {
            GatewayErrorResponse errorResponse =
                    GatewayErrorResponse.of(errorCode.code(), errorCode.message());
            byte[] responseBody = objectMapper.writeValueAsBytes(errorResponse);
            exchange.getResponse().setStatusCode(errorCode.status());
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(responseBody);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception exception) {
            return Mono.error(exception);
        }
    }
}
