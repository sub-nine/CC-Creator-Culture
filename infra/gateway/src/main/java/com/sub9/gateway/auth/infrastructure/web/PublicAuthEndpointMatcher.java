package com.sub9.gateway.auth.infrastructure.web;

import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

@Component
// 인증이 필요 없는 요청인지 판단
public class PublicAuthEndpointMatcher {

    private static final Set<PublicEndpoint> PUBLIC_ENDPOINTS = Set.of(
            new PublicEndpoint(HttpMethod.POST, "/api/v1/auth/signup/customer"),
            new PublicEndpoint(HttpMethod.POST, "/api/v1/auth/signup/creator"),
            new PublicEndpoint(HttpMethod.POST, "/api/v1/auth/login"),
            new PublicEndpoint(HttpMethod.POST, "/api/v1/auth/reissue"),
            new PublicEndpoint(HttpMethod.GET, "/api/v1/products"),
            new PublicEndpoint(HttpMethod.GET, "/api/v1/products/*"),
            new PublicEndpoint(HttpMethod.GET, "/api/v1/products/*/reviews"));

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public boolean matches(ServerHttpRequest request) {
        String path = request.getPath().pathWithinApplication().value();

    return PUBLIC_ENDPOINTS.stream()
        .anyMatch(
            endpoint ->
                request.getMethod() == endpoint.method && pathMatcher.match(endpoint.path(), path));
    }

    private record PublicEndpoint(HttpMethod method, String path) {}
}
