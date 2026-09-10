package com.sub9.gateway.auth.infrastructure.web;

import java.util.List;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
// 외부에서 위조한 내부 인증 헤더를 제거
public class InternalAuthenticationHeaderSanitizer {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String TOKEN_ID_HEADER = "X-Token-Id";
    public static final String TOKEN_EXPIRES_AT_HEADER = "X-Token-Expires-At";

    private static final List<String> INTERNAL_AUTHENTICATION_HEADERS = List.of(
            USER_ID_HEADER,
            USER_ROLE_HEADER,
            TOKEN_ID_HEADER,
            TOKEN_EXPIRES_AT_HEADER);

    // 외부 요청에 포함된 위조 가능한 내부 인증 헤더를 제거한 새 요청을 반환
    public ServerHttpRequest sanitize(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> INTERNAL_AUTHENTICATION_HEADERS.forEach(
                        headerName -> headers.remove(headerName)
                ))
                .build();
    }
}
