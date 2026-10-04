package com.hls.identity.auth;

import com.hls.identity.auth.AuthDtos.AuthResponse;
import com.hls.identity.auth.AuthDtos.SignedInUser;
import com.hls.identity.clientcontext.ClientContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * Builds the 200 response of every sign-in path (password, OTP, renewal) and the logout/failure
 * cookie handling, choosing by client (spec 018 research.md §3): the web gets the renewal credential
 * in an {@code HttpOnly} cookie, the Android app gets it in the body and never sees a cookie.
 */
public final class SignInResponses {

    private SignInResponses() {}

    public static boolean isAndroid() {
        return ClientContextHolder.current().isAndroid();
    }

    public static ResponseEntity<AuthResponse> ok(
            String accessToken,
            long expiresInSeconds,
            SignedInUser user,
            String plainRenewalCredential,
            boolean cookieSecure,
            long renewalTtlDays) {
        if (isAndroid()) {
            return ResponseEntity.ok(new AuthResponse(accessToken, expiresInSeconds, user, plainRenewalCredential));
        }
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        RenewalCookies.issue(plainRenewalCredential, cookieSecure, renewalTtlDays)
                                .toString())
                .body(new AuthResponse(accessToken, expiresInSeconds, user));
    }

    /** Adds the cookie-expiry header for web clients only; the Android app has no cookie to expire. */
    public static ResponseEntity.BodyBuilder expiringCookieForWeb(ResponseEntity.BodyBuilder builder, boolean cookieSecure) {
        if (!isAndroid()) {
            builder.header(HttpHeaders.SET_COOKIE, RenewalCookies.expire(cookieSecure).toString());
        }
        return builder;
    }
}
