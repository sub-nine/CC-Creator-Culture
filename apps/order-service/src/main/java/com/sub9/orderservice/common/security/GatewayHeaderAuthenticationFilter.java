package com.sub9.orderservice.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String TOKEN_ID_HEADER = "X-Token-Id";
    public static final String TOKEN_EXPIRES_AT_HEADER = "X-Token-Expires-At";

    private final AuthenticationEntryPoint authenticationEntryPoint;

    public GatewayHeaderAuthenticationFilter(AuthenticationEntryPoint authenticationEntryPoint) {
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        GatewayAuthenticationPrincipal principal;
        try {
            principal = new GatewayAuthenticationPrincipal(
                    requiredUuidHeader(request, USER_ID_HEADER),
                    GatewayAuthenticationPrincipal.Role.valueOf(requiredHeader(request, USER_ROLE_HEADER)),
                    requiredUuidHeader(request, TOKEN_ID_HEADER),
                    Long.parseLong(requiredHeader(request, TOKEN_EXPIRES_AT_HEADER)));
        } catch (IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid gateway authentication headers", exception));
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing gateway authentication header");
        }
        return value;
    }

    private UUID requiredUuidHeader(HttpServletRequest request, String name) {
        String value = requiredHeader(request, name);
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Invalid gateway UUID header");
        }
        return parsed;
    }
}
