package com.hls.identity.auth;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/** Builds the {@code HttpOnly}/{@code Secure}/{@code SameSite=Strict} renewal-credential cookie
 * (research.md §3) shared by every sign-in path (password, OTP, renewal). */
public final class RenewalCookies {

    public static final String COOKIE_NAME = "renewal_credential";

    private RenewalCookies() {}

    public static ResponseCookie issue(String plainValue, boolean secure, long ttlDays) {
        return ResponseCookie.from(COOKIE_NAME, plainValue)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(ttlDays))
                .build();
    }

    public static ResponseCookie expire(boolean secure) {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }
}
