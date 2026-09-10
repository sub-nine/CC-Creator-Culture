package com.sub9.gateway.auth.infrastructure.filter;

import com.sub9.gateway.auth.domain.exception.InvalidAccessTokenException;
import com.sub9.gateway.auth.domain.model.AccessTokenClaims;
import com.sub9.gateway.auth.infrastructure.jwt.JwtAccessTokenValidator;
import com.sub9.gateway.auth.infrastructure.redis.RedisAccessTokenBlacklistChecker;
import com.sub9.gateway.auth.infrastructure.web.InternalAuthenticationHeaderSanitizer;
import com.sub9.gateway.auth.infrastructure.web.InternalAuthenticationHeaderWriter;
import com.sub9.gateway.auth.infrastructure.web.PublicAuthEndpointMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
// Gateway로 들어오는 요청의 JWT 인증을 처리하는 전역 필터
public class GatewayAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final PublicAuthEndpointMatcher publicAuthEndpointMatcher;
    private final InternalAuthenticationHeaderSanitizer headerSanitizer;
    private final JwtAccessTokenValidator accessTokenValidator;
    private final RedisAccessTokenBlacklistChecker blacklistChecker;
    private final InternalAuthenticationHeaderWriter headerWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // 외부 클라이언트가 위조할 수 있는 내부 인증 헤더를 먼저 제거
        ServerHttpRequest sanitizedRequest = headerSanitizer.sanitize(exchange.getRequest());
        ServerWebExchange sanitizedExchange = exchange.mutate()
                .request(sanitizedRequest)
                .build();
        // 공개 요청은 JWT와 Redis를 검증하지 않고 정제된 상태로 전달
        if (publicAuthEndpointMatcher.matches(sanitizedRequest)) {
            return chain.filter(sanitizedExchange);
        }

        // 보호 요청은 Bearer Token 추출부터 내부 헤더 작성까지
        return Mono.fromCallable(() -> extractBearerToken(sanitizedRequest))
                // JWT 서명, 알고리즘, 필수 Claim, 시간과 ACCESS 토큰 타입을 검증
                .map(accessToken -> accessTokenValidator.validate(accessToken))
                // 검증된 jti로 Redis 블랙리스트를 확인하고 검증에 성공하면 다음 단계에서 사용할 Claim을 유지
                .flatMap(claims -> blacklistChecker.verifyNotBlacklisted(claims.tokenId())
                        .thenReturn(claims))
                // 검증된 Claim을 내부 인증 헤더로 작성하고 다음 필터로 전달
                .flatMap(claims -> forwardAuthenticatedRequest(sanitizedExchange, chain, claims));
    }

    // JWT 인증이 실제 서비스 라우팅보다 먼저 수행되도록 Gateway 필터 중 가장 높은 우선순위를 사용
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    // Authorization 헤더에서 Bearer Access Token을 추출
    private String extractBearerToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new InvalidAccessTokenException();
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new InvalidAccessTokenException();
        }
        return token;
    }

    // 검증된 Access Token Claim을 내부 인증 헤더로 작성하고 인증된 요청을 다음 Gateway 필터로 전달
    private Mono<Void> forwardAuthenticatedRequest(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            AccessTokenClaims claims) {
        ServerHttpRequest authenticatedRequest = headerWriter.write(exchange.getRequest(), claims);
        ServerWebExchange authenticatedExchange = exchange.mutate()
                .request(authenticatedRequest)
                .build();
        return chain.filter(authenticatedExchange);
    }
}
