package com.sub9.userservice.auth.infrastructure.security;

import com.sub9.common.security.CustomAuthenticationEntryPoint;
import com.sub9.userservice.user.domain.model.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/v1/auth/signup/customer",
            "/api/v1/auth/signup/creator",
            "/api/v1/auth/login",
            "/api/v1/auth/reissue");

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String TOKEN_ID_HEADER = "X-Token-Id";
    public static final String TOKEN_EXPIRES_AT_HEADER = "X-Token-Expires-At";

    private final CustomAuthenticationEntryPoint authenticationEntryPoint;

    public GatewayHeaderAuthenticationFilter(CustomAuthenticationEntryPoint authenticationEntryPoint) {
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return PUBLIC_AUTH_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            GatewayAuthenticationPrincipal principal = new GatewayAuthenticationPrincipal(
                    UUID.fromString(requiredHeader(request, USER_ID_HEADER)),
                    UserRole.valueOf(requiredHeader(request, USER_ROLE_HEADER)),
                    UUID.fromString(requiredHeader(request, TOKEN_ID_HEADER)),
                    Long.parseLong(requiredHeader(request, TOKEN_EXPIRES_AT_HEADER)));

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid gateway authentication headers", exception));
        }
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing gateway authentication header");
        }
        return value;
    }
}
