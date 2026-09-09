package com.sub9.gateway.auth.infrastructure.web;

import com.sub9.gateway.auth.domain.model.AccessTokenClaims;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
// 검증된 AccessTokenClaims를 내부 헤더로 변환
public class InternalAuthenticationHeaderWriter {

    public ServerHttpRequest write(ServerHttpRequest request, AccessTokenClaims claims) {
        return request.mutate()
                .headers(headers -> {
                    headers.set(
                            InternalAuthenticationHeaderSanitizer.USER_ID_HEADER,
                            claims.userId().toString());
                    headers.set(
                            InternalAuthenticationHeaderSanitizer.USER_ROLE_HEADER,
                            claims.role().name());
                    headers.set(
                            InternalAuthenticationHeaderSanitizer.TOKEN_ID_HEADER,
                            claims.tokenId().toString());
                    headers.set(
                            InternalAuthenticationHeaderSanitizer.TOKEN_EXPIRES_AT_HEADER,
                            Long.toString(claims.expiresAt().getEpochSecond()));
                })
                .build();
    }
}
