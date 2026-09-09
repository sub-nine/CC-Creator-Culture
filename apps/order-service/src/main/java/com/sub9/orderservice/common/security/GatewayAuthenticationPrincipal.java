package com.sub9.orderservice.common.security;

import java.util.UUID;

public record GatewayAuthenticationPrincipal(
        UUID userId,
        Role role) {

    public enum Role {
        CUSTOMER,
        CREATOR,
        MANAGER,
        MASTER
    }
}
