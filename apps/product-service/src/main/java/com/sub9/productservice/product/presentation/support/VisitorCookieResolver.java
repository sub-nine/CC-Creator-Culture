package com.sub9.productservice.product.presentation.support;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sub9.productservice.common.security.AuthUser;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;

@UtilityClass
public class VisitorCookieResolver {
  public static final String VISITOR_COOKIE = "visitor_cookie";

  public String resolve(AuthUser authUser, String visitorCookie, HttpServletResponse response) {
    if (authUser != null) {
      return "user:" + authUser.id();
    }

    if (StringUtils.hasText(visitorCookie) && isValidCookie(visitorCookie)) {
      return visitorCookie;
    }

    String visitorId = "guest:" + UuidCreator.getTimeOrderedEpoch();
    response.addHeader(HttpHeaders.SET_COOKIE, createCookie(visitorId).toString());

    return visitorId;
  }

  private ResponseCookie createCookie(String visitorId) {
    return ResponseCookie.from(VISITOR_COOKIE, visitorId)
        .httpOnly(true)
        .secure(false) // TODO : Https 적용시 True
        .sameSite("Lax")
        .path("/api/v1/products")
        .maxAge(Duration.ofDays(30))
        .build();
  }

  private boolean isValidCookie(String cookie) {
    if (!cookie.startsWith("guest:")) {
      return false;
    }

    try {
      UUID.fromString(cookie.substring("guest:".length()));
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
