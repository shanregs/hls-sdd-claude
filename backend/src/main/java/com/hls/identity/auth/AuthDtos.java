package com.hls.identity.auth;

import java.util.List;
import java.util.UUID;

/** Request/response shapes for {@code contracts/auth-api.md}. */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(String identifier, String password) {}

    public record SignedInUser(UUID id, String displayName, List<String> roles) {}

    public record AuthResponse(String accessToken, long expiresInSeconds, SignedInUser user) {}

    public record ErrorResponse(String message) {}
}
