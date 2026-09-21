package com.hls.identity.internal;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * REST endpoints per contracts/identity-api.yaml. Every endpoint here is
 * pre-authentication (permitAll in {@link SecurityConfig}) except sessions/
 * logout/unlock, which need a real caller identity.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final AuthenticationService authenticationService;
    private final CurrentUserExtractor currentUserExtractor;
    private final OtpRateLimiter otpRateLimiter;

    public AuthController(AuthenticationService authenticationService, CurrentUserExtractor currentUserExtractor,
                           OtpRateLimiter otpRateLimiter) {
        this.authenticationService = authenticationService;
        this.currentUserExtractor = currentUserExtractor;
        this.otpRateLimiter = otpRateLimiter;
    }

    // ---- DTOs (mirror contracts/identity-api.yaml) -----------------------------------

    public record LoginRequest(String phoneNumber, String password, String deviceLabel) {
    }

    public record MfaVerifyRequest(UUID mfaChallengeId, String code, String deviceLabel) {
    }

    public record OtpRequestRequest(String phoneNumber) {
    }

    public record OtpVerifyRequest(String phoneNumber, String code, Channel channel, String deviceLabel) {
    }

    public record PasswordResetRequestRequest(String identifier) {
    }

    public record PasswordResetConfirmRequest(String resetToken, String newPassword) {
    }

    public record TokenPairResponse(String accessToken, long expiresIn) {
    }

    public record MfaChallengeResponse(UUID mfaChallengeId, MfaMethod method) {
    }

    public record GenericAuthError(String message) {
        static final GenericAuthError DEFAULT = new GenericAuthError("Incorrect phone number or password.");
    }

    // ---- User Story 1: staff password login, MFA, password reset --------------------

    @PostMapping("/login")
    public Object login(@RequestBody LoginRequest request, HttpServletResponse response) {
        LoginOutcome outcome = authenticationService.login(
                request.phoneNumber(), request.password(), Channel.WEB, request.deviceLabel(), requestId());

        return switch (outcome) {
            case LoginOutcome.Authenticated authenticated -> {
                setRefreshCookie(response, authenticated.tokens().rawRefreshToken());
                yield new TokenPairResponse(authenticated.tokens().accessToken(), authenticated.tokens().expiresIn());
            }
            case LoginOutcome.MfaRequired mfaRequired ->
                    new MfaChallengeResponse(mfaRequired.mfaChallengeId(), mfaRequired.method());
        };
    }

    @PostMapping("/mfa/verify")
    public TokenPairResponse verifyMfa(@RequestBody MfaVerifyRequest request, HttpServletResponse response) {
        TokenService.IssuedTokens tokens = authenticationService.verifyMfa(
                request.mfaChallengeId(), request.code(), Channel.WEB, request.deviceLabel(), requestId());
        setRefreshCookie(response, tokens.rawRefreshToken());
        return new TokenPairResponse(tokens.accessToken(), tokens.expiresIn());
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@RequestBody PasswordResetRequestRequest request) {
        authenticationService.requestPasswordReset(request.identifier(), requestId());
    }

    @PostMapping("/password-reset/confirm")
    public void confirmPasswordReset(@RequestBody PasswordResetConfirmRequest request) {
        authenticationService.confirmPasswordReset(request.resetToken(), request.newPassword(), requestId());
    }

    // ---- User Story 2: Teacher OTP login ----------------------------------------------

    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestOtp(@RequestBody OtpRequestRequest request) {
        // FR-009: checked before touching AuthenticationService at all, so a
        // rate-limited caller never even reaches the "does this number exist" logic.
        if (!otpRateLimiter.tryConsume(request.phoneNumber())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }
        authenticationService.requestOtp(request.phoneNumber());
    }

    @PostMapping("/otp/verify")
    public TokenPairResponse verifyOtp(@RequestBody OtpVerifyRequest request, HttpServletResponse response) {
        Channel channel = request.channel() != null ? request.channel() : Channel.WEB;
        TokenService.IssuedTokens tokens = authenticationService.verifyOtp(
                request.phoneNumber(), request.code(), channel, request.deviceLabel(), requestId());
        setRefreshCookie(response, tokens.rawRefreshToken());
        return new TokenPairResponse(tokens.accessToken(), tokens.expiresIn());
    }

    // ---- User Story 1 (FR-011): silent renewal ---------------------------------------

    @PostMapping("/refresh")
    public TokenPairResponse refresh(@CookieValue(REFRESH_COOKIE_NAME) String rawRefreshToken,
                                       HttpServletResponse response) {
        // Pre-authentication (no bearer token expected) — AuthenticationService.refresh
        // resolves (userId, roles) from the session the refresh token itself points to.
        TokenService.RefreshResult result = authenticationService.refresh(rawRefreshToken);
        setRefreshCookie(response, result.rawRefreshToken());
        return new TokenPairResponse(result.accessToken(), result.expiresIn());
    }

    // ---- User Story 5: sessions -------------------------------------------------------

    @GetMapping("/sessions")
    public List<AuthenticationService.SessionSummary> listSessions(
            @RequestParam(required = false) UUID userId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID callerId = currentUserExtractor.userId(jwt);
        Set<Role> callerRoles = currentUserExtractor.roles(jwt);
        return authenticationService.listSessions(callerId, callerRoles, userId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@PathVariable UUID sessionId, @AuthenticationPrincipal Jwt jwt) {
        UUID callerId = currentUserExtractor.userId(jwt);
        Set<Role> callerRoles = currentUserExtractor.roles(jwt);
        authenticationService.revokeSession(sessionId, callerId, callerRoles, requestId());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt) {
        UUID callerId = currentUserExtractor.userId(jwt);
        Set<Role> callerRoles = currentUserExtractor.roles(jwt);
        UUID sessionId = currentUserExtractor.sessionId(jwt);
        authenticationService.revokeSession(sessionId, callerId, callerRoles, requestId());
    }

    // ---- User Story 4: unlock (Admin/Director-only) -----------------------------------

    @PostMapping("/users/{userId}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlockAccount(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        Set<Role> callerRoles = currentUserExtractor.roles(jwt);
        if (!callerRoles.contains(Role.ADMIN) && !callerRoles.contains(Role.DIRECTOR)) {
            throw new AccessDeniedException("Only Admin or Director may unlock an account");
        }
        authenticationService.unlockAccount(userId, requestId());
    }

    // ---- helpers -----------------------------------------------------------------------

    private void setRefreshCookie(HttpServletResponse response, String rawRefreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private String requestId() {
        return com.hls.CorrelationIdFilter.currentCorrelationId();
    }
}
