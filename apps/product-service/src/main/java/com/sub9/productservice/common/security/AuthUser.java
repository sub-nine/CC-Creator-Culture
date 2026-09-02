package com.sub9.productservice.common.security;

import java.util.UUID;

public record AuthUser(UUID id, String role) {}
