package com.sub9.userservice.auth.presentation.response;

public record TokenReissueResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
