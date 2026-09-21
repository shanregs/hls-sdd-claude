package com.hls.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Single source of truth for how refresh tokens are hashed for DB lookup
 * (SHA-256, not BCrypt — lookups need deterministic equality, not a slow
 * per-comparison KDF). {@link TokenService} and {@link AuthenticationService}
 * both call this rather than keeping their own copies, which would risk drift.
 */
final class TokenHashing {

    private TokenHashing() {
    }

    static String sha256Base64(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
