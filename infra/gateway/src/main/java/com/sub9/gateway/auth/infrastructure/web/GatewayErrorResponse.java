package com.sub9.gateway.auth.infrastructure.web;

import java.util.List;
import java.util.Map;

// WebFlux Gateway가 MVC 기반 libs/common에 의존하지 않도록 분리한 오류 응답
public record GatewayErrorResponse(
        String errorCode, String message, List<Map<String, String>> errors) {

    public static GatewayErrorResponse of(String errorCode, String message) {
        return new GatewayErrorResponse(errorCode, message, List.of());
    }
}
