package com.hls.identity.auth;

import java.util.List;
import java.util.UUID;

/** Request/response shapes for {@code contracts/auth-api.md}. */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(String identifier, String password) {}

    public record SignedInUser(UUID id, String displayName, List<String> roles) {}

    /**
     * {@code renewalCredential} is only present for the Android app (which has no cookie jar it can
     * trust, spec 018 research.md §3); it is omitted from the JSON for the web, which keeps the
     * credential in an HttpOnly cookie.
     */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record AuthResponse(String accessToken, long expiresInSeconds, SignedInUser user, String renewalCredential) {

        public AuthResponse(String accessToken, long expiresInSeconds, SignedInUser user) {
            this(accessToken, expiresInSeconds, user, null);
        }
    }

    /** Body of {@code POST /api/v1/auth/renew} for the Android app. */
    public record RenewRequest(String renewalCredential) {}

    public record ErrorResponse(String message) {}
}
