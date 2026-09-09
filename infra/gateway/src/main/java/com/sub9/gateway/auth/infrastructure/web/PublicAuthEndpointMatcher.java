package com.sub9.gateway.auth.infrastructure.web;

import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
public class PublicAuthEndpointMatcher {

    private static final Set<PublicEndpoint> PUBLIC_ENDPOINTS = Set.of(
            new PublicEndpoint(HttpMethod.POST, "/api/v1/auth/signup/customer"),
            new PublicEndpoint(HttpMethod.POST, "/api/v1/auth/signup/creator"),
            new PublicEndpoint(HttpMethod.POST, "/api/v1/auth/login"),
            new PublicEndpoint(HttpMethod.POST, "/api/v1/auth/reissue"));

    public boolean matches(ServerHttpRequest request) {
        String path = request.getPath().pathWithinApplication().value();
        return PUBLIC_ENDPOINTS.contains(new PublicEndpoint(request.getMethod(), path));
    }

    private record PublicEndpoint(HttpMethod method, String path) {
    }
}
